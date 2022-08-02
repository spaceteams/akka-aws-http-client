package de.spaceteams.aws.integration

import de.spaceteams.aws.AwsClientSpec
import org.scalatest.Assertion
import org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType

import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class DynamoDBSpec
    extends AwsClientSpec[DynamoDbAsyncClient, DynamoDbAsyncClientBuilder] {

  val service = DYNAMODB

  def clientBuilder: DynamoDbAsyncClientBuilder = DynamoDbAsyncClient.builder()

  def withTable(
      client: DynamoDbAsyncClient,
      tableName: String = UUID.randomUUID.toString
  )(fct: (String) => Future[Assertion]): Future[Assertion] = {
    val attributes = AttributeDefinition.builder
      .attributeName("id")
      .attributeType(ScalarAttributeType.S)
      .build()
    val keySchema = KeySchemaElement.builder
      .attributeName("id")
      .keyType(KeyType.HASH)
      .build()

    client
      .createTable(
        CreateTableRequest
          .builder()
          .tableName(tableName)
          .attributeDefinitions(attributes)
          .keySchema(keySchema)
          .provisionedThroughput(
            ProvisionedThroughput.builder
              .readCapacityUnits(1000L)
              .writeCapacityUnits(1000L)
              .build()
          )
          .build()
      )
      .asScala
      .flatMap(_ => fct(tableName))
      .andThen { _ =>
        client
          .deleteTable(
            DeleteTableRequest.builder().tableName(tableName).build()
          )
      }
  }
  "A DynamoDB client" should {
    "create a table" in { client =>
      val tableName = UUID.randomUUID().toString()
      val attributes = AttributeDefinition.builder
        .attributeName("id")
        .attributeType(ScalarAttributeType.S)
        .build()
      val keySchema = KeySchemaElement.builder
        .attributeName("id")
        .keyType(KeyType.HASH)
        .build()
      (for {
        _ <- client
          .createTable(
            CreateTableRequest
              .builder()
              .tableName(tableName)
              .attributeDefinitions(attributes)
              .keySchema(keySchema)
              .provisionedThroughput(
                ProvisionedThroughput.builder
                  .readCapacityUnits(1000L)
                  .writeCapacityUnits(1000L)
                  .build()
              )
              .build()
          )
          .asScala
        tables <- client.listTables().asScala.map(_.tableNames().asScala)
      } yield (tables)) map { tables =>
        tables should contain(tableName)
      }
    }

    "puts and gets table data" in { client =>
      withTable(client) { tableName =>
        val data = UUID.randomUUID().toString()

        (for {
          _ <- client
            .putItem(
              PutItemRequest
                .builder()
                .tableName(tableName)
                .item(Map("id" -> AttributeValue.fromS(data)).asJava)
                .build()
            )
            .asScala
          result <- client
            .getItem(
              GetItemRequest
                .builder()
                .tableName(tableName)
                .key(Map("id" -> AttributeValue.fromS(data)).asJava)
                .build()
            )
            .asScala
            .map(_.item().get("id").s())
        } yield (result)) map { result =>
          result should be(data)
        }
      }
    }
  }
}
