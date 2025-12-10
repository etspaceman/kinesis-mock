import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object LibraryDependencies {
  val ScodecBits = Def.setting("org.scodec" %%% "scodec-bits" % "1.2.4")
  val ScalaParserCombinators = Def.setting(
    "org.scala-lang.modules" %%% "scala-parser-combinators" % "2.4.0"
  )
  val Logback = "ch.qos.logback" % "logback-classic" % "1.5.21"

  object Borer {
    val borerVersion = "1.16.2"
    val core = Def.setting("io.bullet" %%% "borer-core" % borerVersion)
    val circe = Def.setting("io.bullet" %%% "borer-compat-circe" % borerVersion)
  }

  object Log4Cats {
    val log4CatsVersion = "2.7.1"
    val core =
      Def.setting("org.typelevel" %%% "log4cats-core" % log4CatsVersion)
    val slf4j =
      "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion
  }

  object Munit {
    val core = Def.setting("org.scalameta" %%% "munit" % "1.2.1")
    val scalacheck =
      Def.setting("org.scalameta" %%% "munit-scalacheck" % "1.2.0")
    val catsEffect =
      Def.setting("org.typelevel" %%% "munit-cats-effect" % "2.1.0")
    val scalacheckEffect =
      Def.setting("org.typelevel" %%% "scalacheck-effect-munit" % "2.0.0-M2")
  }

  object Aws {
    val sdkVersion = "2.40.5"
    val utils = "software.amazon.awssdk" % "utils" % sdkVersion
    val kinesis = "software.amazon.awssdk" % "kinesis" % sdkVersion
    val cloudwatch = "software.amazon.awssdk" % "cloudwatch" % sdkVersion
    val kpl = "software.amazon.kinesis" % "amazon-kinesis-producer" % "1.0.6"
    val kcl = "software.amazon.kinesis" % "amazon-kinesis-client" % "3.2.1"
  }

  object Cats {
    val catsVersion = "2.13.0"
    val catsEffectVersion = "3.6.3"
    val core = Def.setting("org.typelevel" %%% "cats-core" % catsVersion)
    val effect =
      Def.setting("org.typelevel" %%% "cats-effect" % catsEffectVersion)
  }

  object Http4s {
    val http4sVersion = "0.23.33"
    val circe = Def.setting("org.http4s" %%% "http4s-circe" % http4sVersion)
    val dsl = Def.setting("org.http4s" %%% "http4s-dsl" % http4sVersion)
    val emberServer =
      Def.setting("org.http4s" %%% "http4s-ember-server" % http4sVersion)
  }

  object Circe {
    val circeVersion = "0.14.15"
    val core = Def.setting("io.circe" %%% "circe-core" % circeVersion)
    val parser = Def.setting("io.circe" %%% "circe-parser" % circeVersion)
    val fs2 = Def.setting("io.circe" %%% "circe-fs2" % "0.14.1")
  }

  object Ciris {
    val cirisVersion = "3.11.1"
    val core = Def.setting("is.cir" %%% "ciris" % cirisVersion)
    val enumeratum = Def.setting("is.cir" %%% "ciris-enumeratum" % cirisVersion)
  }

  object Enumeratum {
    val enumeratumVersion = "1.9.1"
    val cats =
      Def.setting("com.beachape" %%% "enumeratum-cats" % enumeratumVersion)
    val core = Def.setting("com.beachape" %%% "enumeratum" % enumeratumVersion)
    val circe =
      Def.setting("com.beachape" %%% "enumeratum-circe" % enumeratumVersion)
    val scalacheck =
      Def.setting(
        "com.beachape" %%% "enumeratum-scalacheck" % enumeratumVersion
      )
  }

  object FS2 {
    val fs2Version = "3.12.2"
    val core = Def.setting("co.fs2" %%% "fs2-core" % fs2Version)
    val io = Def.setting("co.fs2" %%% "fs2-io" % fs2Version)
  }

  object Refined {
    val refinedVersion = "0.11.3"
    val scalacheck =
      Def.setting("eu.timepit" %%% "refined-scalacheck" % refinedVersion)
  }
}
