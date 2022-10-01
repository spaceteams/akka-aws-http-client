# An Akka HTTP based backend client for the AWS Java SDK v2

This package contains an alternative implementation for the Async HTTP Client provided by the AWS Java SDK v2.
It utilizes non-blocking IO based on the Akka HTTP stack, offering a replacement to the built-in Netty HTTP stack.

It supports Scala 2.13+ and Scala 3.1+

## Usage

Import the package from your favorite artifactory

```scala
ThisBuild / libraryDependencies += "de.spaceteams" %% "akka-aws-http-client" % <version>
```

(Optional) Exclude the Netty and Apache HTTP stacks form your AWS SDK dependencies.
This is not strictly necessary, but it reduces your dependency tree by a lot.

```scala
ThisBuild / libraryDependencies ++= Seq(
    "software.amazon.awssdk" % "s3" % AmazonSdkVersion,
    "software.amazon.awssdk" % "sqs" % AmazonSdkVersion,
    "software.amazon.awssdk" % "dynamodb" % AmazonSdkVersion
  ).map(
    _ exclude ("software.amazon.awssdk", "netty-nio-client")
      exclude ("software.amazon.awssdk", "apache-client")
  )
```

To make use of the client, plug it into your AWS SDKs client builder, eg.

```scala

implicit val actorSystem = ActorSystem()

val akkaClient = AkkaAwsHttpClient()

val s3Client = S3AsyncClient
              .builder()
              .httpClient(akkaClient)
              .build()
```

The package does **NOT** provide an SPI interface for the AWS SDK.
This is a deliberate choice, as it does not allow you to easily pass in
the `actorSystem`, which sort of defeats the point.
You may of course provide your own SPI interface using this client if you wish to do so.
