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
      Aws.kinesis % FunctionalTest,
      Aws.kpl % FunctionalTest,
      Aws.kcl % FunctionalTest,
      CatsRetry % FunctionalTest
    ),
    semanticdbEnabled := true,
    semanticdbVersion := "4.4.10",
    ThisBuild / scalafixDependencies += OrganizeImports,
    javacOptions += "-XDignore.symbol.file",
    scalacOptions ++= ScalacSettings.settings,
    Compile / console / scalacOptions ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Ywarn-unused:imports"))
    },
    addCompilerPlugin(KindProjector cross CrossVersion.full),
    addCompilerPlugin(BetterMonadicFor),
    testFrameworks += MUnitFramework,
    Test / testOptions ++= {
      List(Tests.Argument(MUnitFramework, "+l"))
    },
    assembly / test := {}
  )
  .configs(FunctionalTest)
  .settings(
    inConfig(FunctionalTest)(
      ScalafmtPlugin.scalafmtConfigSettings ++
        scalafixConfigSettings(FunctionalTest) ++
        BloopSettings.default ++
        DockerImagePlugin.settings ++
        DockerComposePlugin.settings(FunctionalTest) ++
        Defaults.testSettings ++
        Seq(parallelExecution := false)
    )
  )
  .settings(DockerImagePlugin.settings)
  .settings(DockerComposePlugin.settings(FunctionalTest))
  .settings(
    Seq(
      addCommandAlias("cpl", ";Test / compile;Fun / compile"),
      addCommandAlias(
        "fixCheck",
        ";Compile / scalafix --check;Test / scalafix --check;Fun / scalafix --check"
      ),
      addCommandAlias(
        "fix",
        ";Compile / scalafix;Test / scalafix;Fun / scalafix"
      ),
      addCommandAlias(
        "fmt",
        ";Compile / scalafmt;Test / scalafmt;Fun / scalafmt;scalafmtSbt"
      ),
      addCommandAlias(
        "fmtCheck",
        ";Compile / scalafmtCheck;Test / scalafmtCheck;Fun / scalafmtCheck;scalafmtSbtCheck"
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
        "cov",
        ";clean;coverage;test;Fun / dockerComposeTestQuick;coverageReport"
      ),
      addCommandAlias(
        "validate",
        ";cov;prettyCheck"
      )
    ).flatten
  )
