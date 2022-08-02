package de.spaceteams.aws.integration

import de.spaceteams.aws.AwsClientSpec
import org.scalatest.Assertion
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class SqsSpec extends AwsClientSpec[SqsAsyncClient, SqsAsyncClientBuilder] {

  val service = SQS

  override def clientBuilder: SqsAsyncClientBuilder = SqsAsyncClient.builder()

  def withQueue(
      client: SqsAsyncClient,
      queueName: String = UUID.randomUUID.toString
  )(fct: (String) => Future[Assertion]): Future[Assertion] = {
    val req = CreateQueueRequest.builder().queueName(queueName).build()
    client
      .createQueue(req)
      .asScala
      .flatMap(resp =>
        fct(resp.queueUrl()).andThen { _ =>
          client
            .deleteQueue(
              DeleteQueueRequest.builder().queueUrl(resp.queueUrl()).build()
            )
        }
      )
  }
  "An SqsClient" should {
    "create a queue" in { client =>
      (for {
        _ <- client
          .createQueue(
            CreateQueueRequest.builder().queueName("some-queue").build()
          )
          .asScala
        queues <- client
          .listQueues()
          .asScala
          .map(_.queueUrls().asScala.map(_.split("/").last))
      } yield (queues)) map { queues =>
        queues should contain("some-queue")
      }
    }

    "publishes a message" in { client =>
      withQueue(client) { queueUrl =>
        val data = UUID.randomUUID().toString()

        val req =
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(data)
            .build()
        (for {
          _ <- client.sendMessage(req).asScala
          msgs <- client
            .receiveMessage(
              ReceiveMessageRequest.builder().queueUrl(queueUrl).build()
            )
            .asScala
            .map(_.messages().asScala.map(_.body()))
        } yield (msgs)) map { msgs =>
          msgs should have length (1)
          msgs should contain only (data)
        }
      }
    }

    "deletes a message" in { client =>
      withQueue(client) { queueUrl =>
        val data = UUID.randomUUID().toString()

        val req =
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(data)
            .build()
        (for {
          _ <- client.sendMessage(req).asScala
          msg <- client
            .receiveMessage(
              ReceiveMessageRequest
                .builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .build()
            )
            .asScala
            .map(_.messages().asScala(0))
          _ <- client
            .deleteMessage(
              DeleteMessageRequest
                .builder()
                .queueUrl(queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build()
            )
            .asScala
          msgs <- client
            .receiveMessage(
              ReceiveMessageRequest
                .builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .build()
            )
            .asScala
            .map(_.messages().asScala)
        } yield (msgs)) map { msgs =>
          msgs shouldBe empty
        }
      }
    }
  }
}
