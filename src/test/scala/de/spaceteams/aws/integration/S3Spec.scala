package de.spaceteams.aws.integration
import de.spaceteams.aws.AwsClientSpec
import org.scalatest.compatible.Assertion
import org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest

import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class S3Spec extends AwsClientSpec[S3AsyncClient, S3AsyncClientBuilder] {
  val service = S3
  def clientBuilder: S3AsyncClientBuilder = S3AsyncClient.builder()

  def withBucket(
      client: S3AsyncClient,
      bucketName: String = UUID.randomUUID.toString
  )(fct: (String) => Future[Assertion]): Future[Assertion] = {
    val req = CreateBucketRequest.builder().bucket(bucketName).build()
    client.createBucket(req).asScala.flatMap(_ => fct(bucketName)).andThen {
      _ =>
        client
          .deleteBucket(
            DeleteBucketRequest.builder().bucket(bucketName).build()
          )
    }
  }

  "An S3 client" should {
    "create a bucket" in { client =>
      val bucketName = "my-bucket"
      val req = CreateBucketRequest.builder().bucket(bucketName).build()
      (for {
        _ <- client.createBucket(req).asScala
        resp <- client.listBuckets().asScala
        buckets = resp.buckets().asScala.map(_.name)
      } yield buckets) map (_ should contain(bucketName))
    }

    "put and get an object" in { client =>
      withBucket(client) { bucketName =>
        val data = UUID.randomUUID().toString()

        val put =
          PutObjectRequest.builder().bucket(bucketName).key("my-key").build()
        (for {
          _ <- client
            .putObject(put, AsyncRequestBody.fromString(data))
            .asScala
          get = GetObjectRequest
            .builder()
            .bucket(bucketName)
            .key("my-key")
            .build()
          transformer = AsyncResponseTransformer.toBytes[GetObjectResponse]()
          response <- client.getObject(get, transformer).asScala
        } yield response) map (response =>
          response.asUtf8String() should equal(data)
        )
      }
    }

    "upload and get a multipart object" in { client =>
      withBucket(client) { bucketName =>
        val data = UUID.randomUUID().toString() * 1000000

        val partNumbers = Seq(1, 2)

        val multipart = CreateMultipartUploadRequest
          .builder()
          .bucket(bucketName)
          .key("my-multipart")
          .build()
        val part =
          UploadPartRequest.builder().bucket(bucketName).key("my-multipart")

        (for {
          multipartResp <- client
            .createMultipartUpload(multipart)
            .asScala

          uploads <- partNumbers
            .foldLeft(
              Future.successful(List.empty[CompletedPart])
            )((prev, p) =>
              prev.flatMap { list =>
                client
                  .uploadPart(
                    part
                      .copy()
                      .uploadId(multipartResp.uploadId())
                      .partNumber(p)
                      .build(),
                    AsyncRequestBody.fromString(data)
                  )
                  .asScala
                  .map(item =>
                    CompletedPart
                      .builder()
                      .partNumber(p)
                      .eTag(item.eTag())
                      .build() :: list
                  )
              }
            )

          _ <- client
            .completeMultipartUpload(
              CompleteMultipartUploadRequest
                .builder()
                .bucket(bucketName)
                .key("my-multipart")
                .uploadId(multipartResp.uploadId())
                .multipartUpload(
                  CompletedMultipartUpload
                    .builder()
                    .parts(uploads.asJava)
                    .build()
                )
                .build()
            )
            .asScala

          get = GetObjectRequest
            .builder()
            .bucket(bucketName)
            .key("my-multipart")
            .build()
          transformer = AsyncResponseTransformer.toBytes[GetObjectResponse]()
          response <- client.getObject(get, transformer).asScala
        } yield response) map (response =>
          response.asUtf8String() should equal(data * partNumbers.length)
        )
      }
    }
  }
}
