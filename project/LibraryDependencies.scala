import sbt._

object LibraryDependencies {
  val KindProjector = "org.typelevel" % "kind-projector" % "0.11.3"
  val OrganizeImports =
    "com.github.liancheng" %% "organize-imports" % "0.5.0"
  val Logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val BetterMonadicFor = "com.olegpy" %% "better-monadic-for" % "0.3.1"
  val JaxbApi = "javax.xml.bind" % "jaxb-api" % "2.3.1"

  object Munit {
    val munitVersion = "0.7.22"
    val core = "org.scalameta" %% "munit" % munitVersion
    val scalacheck = "org.scalameta" %% "munit-scalacheck" % munitVersion
    val catsEffect2 = "org.typelevel" %% "munit-cats-effect-2" % "0.13.0"
  }

  object Aws {
    val sdkVersion = "2.16.18"
    val utils = "software.amazon.awssdk" % "utils" % sdkVersion
  }

  object Http4s {
    val http4sVersion = "0.21.16"
    val blazeServer = "org.http4s" %% "http4s-blaze-server" % http4sVersion
    val circe = "org.http4s" %% "http4s-circe" % http4sVersion
    val dsl = "org.http4s" %% "http4s-dsl" % http4sVersion
  }

  object Circe {
    val circeVersion = "0.13.0"
    val core = "io.circe" %% "circe-core" % circeVersion
    val parser = "io.circe" %% "circe-parser" % circeVersion
  }

  object Ciris {
    private val cirisVersion = "1.2.1"
    val core = "is.cir" %% "ciris" % cirisVersion
    val enumeratum = "is.cir" %% "ciris-enumeratum" % cirisVersion
  }

  object Enumeratum {
    val enumeratumVersion = "1.6.1"
    val core = "com.beachape" %% "enumeratum" % enumeratumVersion
    val circe = "com.beachape" %% "enumeratum-circe" % enumeratumVersion
    val scalacheck = "com.beachape" %% "enumeratum-scalacheck" % enumeratumVersion
  }

  object Kinesis {
    val kcl = "software.amazon.kinesis" % "amazon-kinesis-client" % "2.3.4"
    val kpl = "com.amazonaws" % "amazon-kinesis-producer" % "0.14.3"
    val sdkV1 = "com.amazonaws" % "aws-java-sdk-kinesis" % "1.11.190"
    val sdkV2 = "software.amazon.awssdk" % "kinesis" % "2.16.14"
  }
}
