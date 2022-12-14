package de.spaceteams.aws.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharset
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpProtocols
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.RequestEntityAcceptance.Expected
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers.`Content-Length`
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import software.amazon.awssdk.http.SdkHttpFullResponse
import software.amazon.awssdk.http.async.AsyncExecuteRequest
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler

import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class AkkaAwsHttpClient(
    connectionPoolSettings: Option[ConnectionPoolSettings] = None,
    http: Option[HttpExt] = None
)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext)
    extends SdkAsyncHttpClient {

  import AkkaAwsHttpClient._

  private val httpClient = http.getOrElse(Http())
  private def httpRequest(request: HttpRequest): Future[HttpResponse] =
    connectionPoolSettings
      .map(settings => httpClient.singleRequest(request, settings = settings))
      .getOrElse(httpClient.singleRequest(request))

  override val clientName = "AkkaAwsHttpClient"

  override def close(): Unit = ()

  override def execute(
      request: AsyncExecuteRequest
  ): CompletableFuture[Void] = {
    val handler = request.responseHandler()
    Try(AkkaAwsHttpClient.toAkkaRequest(request)) match {
      case Failure(e) =>
        handler.onError(e)
        Future.failed(e).asJava.toCompletableFuture()
      case Success(akkaReq) =>
        httpRequest(akkaReq)
          .transform {
            case Success(result) =>
              Try(toAwsSdkResponse(result, handler))
            case Failure(e) =>
              handler.onError(e)
              Failure(e)
          }
          .map(_ => null: Void)
          .asJava
          .toCompletableFuture()
    }
  }
}

object AkkaAwsHttpClient {

  // AWS expects these content type headers to set exactly right,
  // as they are used in the calculation of checksums and cryptograph signatures
  // That means they need to precise on a binary level, including precise
  // case sensetivity and formatting / white space etc.
  // Since HTTP does not define it as such in any standard, AKKA Http "mangels"
  // the values as far as AWS is concerned.
  // To mitigate this behavior, this map contains precise representations of
  // the most known content types that AWS expects and how it expects them.
  //
  // This fixes issues where request signature calculated by the server do not match
  // the signature provided by the client.
  val knownContentTypes: Map[String, ContentType] =
    Map(
      "application/x-amz-json-1.0" -> ContentType(
        MediaType
          .customBinary("application", "x-amz-json-1.0", MediaType.Compressible)
      ),
      "application/x-amz-json-1.1" -> ContentType(
        MediaType
          .customBinary("application", "x-amz-json-1.1", MediaType.Compressible)
      ),
      "application/x-amz-cbor-1.1" -> ContentType(
        MediaType
          .customBinary("application", "x-amz-cbor-1.1", MediaType.Compressible)
      ),
      "application/x-www-form-urlencoded; charset-UTF-8" -> ContentType(
        MediaType.applicationWithOpenCharset("x-www-form-urlencoded"),
        HttpCharset.custom("utf-8")
      ),
      "application/x-www-form-urlencoded" -> ContentType(
        MediaType.applicationWithOpenCharset("x-www-form-urlencoded"),
        HttpCharset.custom("utf-8")
      ),
      "application/xml" -> ContentType(
        MediaType.customBinary("application", "xml", MediaType.Compressible)
      ),
      "binary/octet-stream" -> ContentType(
        MediaType.customBinary(
          "binary",
          "octet-stream",
          MediaType.NotCompressible
        )
      ),
      "text/plain" -> ContentType(
        MediaType.text("plain"),
        HttpCharset.custom("UTF-8")
      )
    )

  private[aws] def toAkkaRequest(request: AsyncExecuteRequest): HttpRequest = {

    val underlyingRequest = request.request()
    val contentPublisher = request.requestContentPublisher()

    val httpMethod =
      HttpMethods
        .getForKeyCaseInsensitive(underlyingRequest.method().name())
        .getOrElse(
          throw new IllegalArgumentException(
            s"Unsupported HTTP method: ${underlyingRequest.method()}"
          )
        )

    val headers =
      underlyingRequest
        .headers()
        .asScala
        .foldLeft(List.empty[HttpHeader]) { (prev, header) =>
          {
            val (name, value) = header
            RawHeader(name, value.asScala.mkString(",")) :: prev
          }
        }

    val requestHeaders = headers
      .filterNot(header =>
        Set(
          `Content-Length`.lowercaseName, // will be reset by akka
          `Content-Type`.lowercaseName // akka expects this header to be set on the HttpEntity
        ).contains(header.lowercaseName)
      )

    val entity = httpMethod.requestEntityAcceptance match {
      case Expected => {
        val contentType =
          headers
            .find(_.is(`Content-Type`.lowercaseName))
            .map(h =>
              ContentType.parse(h.value()) match {
                case Left(value) =>
                  throw new IllegalArgumentException(
                    s"Unsupported content type value: ${h.value}. \n ${value.map(_.formatPretty).mkString("\n")}"
                  )
                case Right(value) =>
                  val key = value.mediaType.value
                  knownContentTypes.getOrElse(
                    key,
                    value
                  )
              }
            )
            .getOrElse(ContentTypes.NoContentType)
        val source = Source.fromPublisher(contentPublisher).map(ByteString(_))
        contentPublisher.contentLength.toScala match {
          case Some(length) =>
            HttpEntity(contentType, length, source)
          case None =>
            HttpEntity(contentType, source)
        }
      }
      case _ => HttpEntity.Empty
    }

    HttpRequest(
      method = httpMethod,
      uri = Uri(underlyingRequest.getUri().toString()),
      headers = requestHeaders,
      entity = entity,
      protocol = HttpProtocols.`HTTP/1.1`
    )

  }

  private[aws] def toAwsSdkResponse(
      response: HttpResponse,
      handler: SdkAsyncHttpResponseHandler
  )(implicit actorSystem: ActorSystem, ec: ExecutionContext): Future[Unit] = {
    val resp = SdkHttpFullResponse
      .builder()
      .headers(
        response.headers
          .groupBy(_.name())
          .map { case (k, v) => k -> v.map(_.value()).asJava }
          .asJava
      )
      .statusCode(response.status.intValue())
      .statusText(response.status.reason)

    response.entity
      .getContentLengthOption()
      .toScala
      .foreach(cl => resp.appendHeader(`Content-Length`.name, cl.toString()))

    resp.appendHeader(
      `Content-Type`.name,
      response.entity.getContentType().toString()
    )

    val (done, content) =
      response.entity.dataBytes
        .map(_.asByteBuffer)
        .alsoToMat(Sink.ignore)(Keep.right)
        .toMat(Sink.asPublisher(fanout = false))(Keep.both)
        .run()

    handler.onHeaders(resp.build())
    handler.onStream(content)

    done.map(_ => ())

  }

  def apply(
      connectionPoolSettings: Option[ConnectionPoolSettings] = None,
      http: Option[HttpExt] = None
  )(implicit actorSystem: ActorSystem): AkkaAwsHttpClient = {
    implicit val ec = actorSystem.dispatcher
    new AkkaAwsHttpClient(connectionPoolSettings, http)
  }
}
