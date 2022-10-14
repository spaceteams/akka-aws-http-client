lazy val scala_2_13 = "2.13.8"
lazy val scala_3_1 = "3.1.1"
lazy val supportedScalaVersions = Seq(scala_2_13)

Global / semanticdbEnabled := true
Global / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / organization := "de.spaceteams"
ThisBuild / licenses := Seq(
  ("BSD-3", url("https://opensource.org/licenses/BSD-3-Clause"))
)
ThisBuild / homepage := Some(
  url("http://github.com/spaceteams/akka-aws-http-client")
)
ThisBuild / description := "An Akka HTTP based backend client for the AWS Java SDK v2"
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("http://github.com/spaceteams/akka-aws-http-client"),
    "scm:git:http://github.com/spaceteams/akka-aws-http-client.git",
    "scm:git:git@github.com:spaceteams/akka-aws-http-client.git"
  )
)

ThisBuild / developers := List(
  Developer(
    "kampka",
    "Christian Kampka",
    "christian.kampka@spaceteams.de",
    url("https://www.spaceteams.de")
  )
)

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

ThisBuild / scalaVersion := scala_2_13
ThisBuild / version := "1.3.0-SNAPSHOT"
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / fork := true
ThisBuild / useCoursier := true

ThisBuild / scalafixScalaBinaryVersion := scalaBinaryVersion.value
ThisBuild / scalafixDependencies := Seq(
  "com.github.liancheng" %% "organize-imports" % "0.6.0"
)

ThisBuild / libraryDependencies ++= (CrossVersion
  .partialVersion(scalaVersion.value) match {
  case Some((2, 13)) =>
    Seq(
      "org.scala-lang" % "scala-library" % scalaVersion.value % Provided
    )
  case _ =>
    Seq(
      "org.scala-lang" % "scala3-library_3" % scalaVersion.value % Provided
    )
}) ++ Seq(
  "org.scalatest" %% "scalatest" % "3.2.11" % Test
)

lazy val commonSettings = Seq(
  scalacOptions := (if (scalaVersion.value.startsWith("3"))
                      Seq("-Xfatal-warnings", "-Ykind-projector")
                    else Seq("-Werror", "-Wunused")) ++ Seq(
    "-deprecation",
    "-feature"
  ),
  crossScalaVersions := supportedScalaVersions
)

val AmazonSdkVersion = "2.17.243"
lazy val amazonSdkTestLibs = (
  Seq(
    "software.amazon.awssdk" % "s3" % AmazonSdkVersion,
    "software.amazon.awssdk" % "sqs" % AmazonSdkVersion,
    "software.amazon.awssdk" % "dynamodb" % AmazonSdkVersion
  ).map(
    _ exclude ("software.amazon.awssdk", "netty-nio-client")
      exclude ("software.amazon.awssdk", "apache-client")
  )
) //.map(_ % Test)

val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "akka-aws-http-client",
    moduleName := "akka-aws-http-client",
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "sdk-core" % AmazonSdkVersion % Provided,
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion % Provided,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion % Provided,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion % Provided,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.10" % Test,
      "com.dimafeng" %% "testcontainers-scala-localstack-v2" % "0.40.10" % Test,
      "com.amazonaws" % "aws-java-sdk-core" % "1.11.959" % Test, // v1 SDK version for localstack
      "org.slf4j" % "slf4j-simple" % "2.0.3" % Test
    ) ++ amazonSdkTestLibs
  )
