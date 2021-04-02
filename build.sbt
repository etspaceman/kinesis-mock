import LibraryDependencies._

val MUnitFramework = new TestFramework("munit.Framework")

lazy val kinesisMock = project
  .in(file("."))
  .enablePlugins(DockerImagePlugin, DockerComposePlugin)
  .settings(
    name := "kinesis-mock",
    organization := "io.github.etspaceman",
    description := "A Mock API for AWS Kinesis",
    scalaVersion := "2.13.5",
    resolvers += Resolver.bintrayRepo("wolfendale", "maven"),
    libraryDependencies ++= Seq(
      Aws.utils,
      Borer.circe,
      Borer.core,
      Cats.core,
      Cats.effect,
      Circe.core,
      Circe.derivation,
      PureConfig.core,
      PureConfig.enumeratum,
      Enumeratum.cats,
      Enumeratum.core,
      Enumeratum.circe,
      Http4s.blazeServer,
      Http4s.circe,
      Http4s.dsl,
      JaxbApi,
      Logback,
      Log4Cats.slf4j,
      GraalSvm % "compile-internal",
      PureConfig.catsEffect,
      PureConfig.core,
      PureConfig.enumeratum,
      UUIDCreator,
      Circe.parser % Test,
      Enumeratum.scalacheck % Test,
      Munit.core % Test,
      Munit.catsEffect2 % Test,
      Munit.scalacheck % Test,
      Munit.scalacheckEffect % Test,
      Refined.scalacheck % Test,
      ScalacheckGenRegexp % Test,
      Aws.kinesis % FunctionalTest
    ),
    semanticdbEnabled := true,
    semanticdbVersion := "4.4.10",
    ThisBuild / scalafixDependencies += OrganizeImports,
    javacOptions += "-XDignore.symbol.file",
    scalacOptions ++= ScalacSettings.settings,
    scalacOptions in (Compile, console) ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Ywarn-unused:imports"))
    },
    addCompilerPlugin(KindProjector cross CrossVersion.full),
    addCompilerPlugin(BetterMonadicFor),
    testFrameworks += MUnitFramework,
    testOptions.in(Test) ++= {
      List(Tests.Argument(MUnitFramework, "+l"))
    },
    test in assembly := {}
  )
  .configs(FunctionalTest)
  .settings(
    inConfig(FunctionalTest)(
      ScalafmtPlugin.scalafmtConfigSettings ++
        scalafixConfigSettings(FunctionalTest) ++
        BloopSettings.default ++
        DockerImagePlugin.settings ++
        DockerComposePlugin.settings(FunctionalTest) ++
        Defaults.testSettings
    )
  )
  .settings(DockerImagePlugin.settings)
  .settings(DockerComposePlugin.settings(FunctionalTest))
  .settings(
    Seq(
      addCommandAlias("cpl", ";test:compile;fun:compile"),
      addCommandAlias(
        "fixCheck",
        ";compile:scalafix --check;test:scalafix --check;fun:scalafix --check"
      ),
      addCommandAlias("fix", ";compile:scalafix;test:scalafix;fun:scalafix"),
      addCommandAlias(
        "fmt",
        ";compile:scalafmt;fun:scalafmt;scalafmtSbt"
      ),
      addCommandAlias(
        "fmtCheck",
        ";compile:scalafmtCheck;fun:scalafmtCheck;scalafmtSbtCheck"
      ),
      addCommandAlias(
        "pretty",
        ";fix;fmt"
      ),
      addCommandAlias(
        "prettyCheck",
        ";fixCheck;fmtCheck"
      ),
      addCommandAlias(
        "validate",
        ";cpl;prettyCheck;test"
      )
    ).flatten
  )
