import LibraryDependencies._

val MUnitFramework = new TestFramework("munit.Framework")

organization := "io.github.etspaceman"
name := "kinesis-mock"
description := "A Mock API for AWS Kinesis"
scalaVersion := "2.13.5"
resolvers += Resolver.bintrayRepo("wolfendale", "maven")
libraryDependencies ++= Seq(
  Aws.utils,
  Http4s.blazeServer,
  Http4s.circe,
  Http4s.dsl,
  Cats.core,
  Cats.effect,
  Circe.core,
  Ciris.core,
  Ciris.enumeratum,
  Enumeratum.cats,
  Enumeratum.core,
  Enumeratum.circe,
  JaxbApi,
  Logback,
  Circe.parser % Test,
  Enumeratum.scalacheck % Test,
  Munit.core % Test,
  Munit.catsEffect2 % Test,
  Munit.scalacheck % Test,
  Munit.scalacheckEffect % Test,
  Refined.scalacheck % Test,
  ScalacheckGenRegexp % Test
)
semanticdbEnabled := true
semanticdbVersion := "4.4.10"
ThisBuild / scalafixDependencies += OrganizeImports
scalacOptions ++= ScalacSettings.settings
scalacOptions in (Compile, console) ~= {
  _.filterNot(Set("-Ywarn-unused-import", "-Ywarn-unused:imports"))
}
addCompilerPlugin(KindProjector cross CrossVersion.full)
addCompilerPlugin(BetterMonadicFor)
testFrameworks += MUnitFramework
testOptions.in(Test) ++= {
  List(Tests.Argument(MUnitFramework, "+l"))
}
addCommandAlias("cpl", ";+test:compile")
addCommandAlias(
  "fixCheck",
  ";compile:scalafix --check ;test:scalafix --check"
)
addCommandAlias("fix", ";compile:scalafix ;test:scalafix")
addCommandAlias(
  "fmt",
  ";scalafmtAll;scalafmtSbt"
)
addCommandAlias(
  "fmtCheck",
  ";scalafmtCheckAll;scalafmtSbtCheck"
)
addCommandAlias(
  "pretty",
  ";fix;fmt"
)
addCommandAlias(
  "prettyCheck",
  ";fixCheck;fmtCheck"
)
addCommandAlias(
  "validate",
  ";cpl;prettyCheck;test"
)
