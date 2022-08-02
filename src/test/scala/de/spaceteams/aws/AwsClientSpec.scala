package de.spaceteams.aws
import akka.actor.ActorSystem
import com.dimafeng.testcontainers.LocalStackV2Container
import de.spaceteams.aws.http.AkkaAwsHttpClient
import org.scalatest.FutureOutcome
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB
import org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.SdkClient

trait AwsClientSpec[CLIENT <: SdkClient, BUILDER <: AwsAsyncClientBuilder[
  BUILDER,
  CLIENT
] with AwsClientBuilder[BUILDER, CLIENT]]
    extends FixtureAsyncWordSpec
    with Matchers {
  import AwsClientSpec._

  protected type FixtureParam = CLIENT

  def clientBuilder: BUILDER

  def service: Service

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    require(enabledServices.contains(service))
    implicit val actorSystem = ActorSystem()
    val client = AkkaAwsHttpClient(None)
    val awsClient = clientBuilder
      .httpClient(client)
      .endpointOverride(container.endpointOverride(service))
      .credentialsProvider(container.staticCredentialsProvider)
      .region(container.region)
      .build()

    complete {
      super.withFixture(test.toNoArgAsyncTest(awsClient))
    } lastly {
      awsClient.close()
      client.close()
    }
  }
}

object AwsClientSpec {

  val enabledServices: Seq[LocalStackV2Container.Service] =
    List(S3, SQS, DYNAMODB)

  lazy val container: LocalStackV2Container = {
    val c = new LocalStackV2Container(services = enabledServices)
    Runtime.getRuntime().addShutdownHook(new Thread(() => c.close()))
    c.start()
    c
  }
}
