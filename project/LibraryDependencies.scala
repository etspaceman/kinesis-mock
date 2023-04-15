import sbt._

object LibraryDependencies {
  val KindProjector = "org.typelevel" % "kind-projector" % "0.13.2"
  val OrganizeImports =
    "com.github.liancheng" %% "organize-imports" % "0.6.0"
  val Logback = "ch.qos.logback" % "logback-classic" % "1.4.6"
  val BetterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"
  val JaxbApi = "javax.xml.bind" % "jaxb-api" % "2.3.1"
  val ScalacheckGenRegexp =
    "io.github.wolfendale" %% "scalacheck-gen-regexp" % "1.1.0"
  val UUIDCreator = "com.github.f4b6a3" % "uuid-creator" % "5.2.0"
  val GraalSvm = "org.graalvm.nativeimage" % "svm" % "22.3.1"
  val CatsRetry = "com.github.cb372" %% "cats-retry" % "3.1.0"
  val OsLib = "com.lihaoyi" %% "os-lib" % "0.9.1"

  object Borer {
    val borerVersion = "1.8.0"
    val core = "io.bullet" %% "borer-core" % borerVersion
    val circe = "io.bullet" %% "borer-compat-circe" % borerVersion
  }

  object Log4Cats {
    val log4CatsVersion = "2.5.0"
    val slf4j = "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion
  }

  object Munit {
    val munitVersion = "0.7.29"
    val core = "org.scalameta" %% "munit" % munitVersion
    val scalacheck = "org.scalameta" %% "munit-scalacheck" % munitVersion
    val catsEffect2 = "org.typelevel" %% "munit-cats-effect-3" % "1.0.7"
    val scalacheckEffect =
      "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4"
  }

  object Aws {
    val sdkVersion = "2.20.45"
    val utils = "software.amazon.awssdk" % "utils" % sdkVersion
    val kinesis = "software.amazon.awssdk" % "kinesis" % sdkVersion
    val kpl = "com.amazonaws" % "amazon-kinesis-producer" % "0.15.5"
    val kcl = "software.amazon.kinesis" % "amazon-kinesis-client" % "2.4.5"
  }

  object Cats {
    val catsVersion = "2.9.0"
    val catsEffectVersion = "3.4.8"
    val core = "org.typelevel" %% "cats-core" % catsVersion
    val effect = "org.typelevel" %% "cats-effect" % catsEffectVersion
  }

  object Http4s {
    val http4sVersion = "0.23.18"
    val circe = "org.http4s" %% "http4s-circe" % http4sVersion
    val dsl = "org.http4s" %% "http4s-dsl" % http4sVersion
    val emberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  }

  object Circe {
    val circeVersion = "0.14.5"
    val core = "io.circe" %% "circe-core" % circeVersion
    val parser = "io.circe" %% "circe-parser" % circeVersion
    val jackson = "io.circe" %% "circe-jackson212" % "0.14.0"
  }

  object PureConfig {
    private val pureConfigVersion = "0.17.3"
    val core = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
    val enumeratum =
      "com.github.pureconfig" %% "pureconfig-enumeratum" % pureConfigVersion
    val catsEffect =
      "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion
  }

  object Enumeratum {
    val enumeratumVersion = "1.7.2"
    val cats = "com.beachape" %% "enumeratum-cats" % enumeratumVersion
    val core = "com.beachape" %% "enumeratum" % enumeratumVersion
    val circe = "com.beachape" %% "enumeratum-circe" % enumeratumVersion
    val scalacheck =
      "com.beachape" %% "enumeratum-scalacheck" % enumeratumVersion
  }

  object Kinesis {
    val kcl = "software.amazon.kinesis" % "amazon-kinesis-client" % "2.4.5"
    val kpl = "com.amazonaws" % "amazon-kinesis-producer" % "0.14.3"
    val sdkV1 = "com.amazonaws" % "aws-java-sdk-kinesis" % "1.11.190"
    val sdkV2 = "software.amazon.awssdk" % "kinesis" % "2.16.14"
  }

  object Refined {
    val refinedVersion = "0.10.3"
    val scalacheck = "eu.timepit" %% "refined-scalacheck" % refinedVersion
  }
}
