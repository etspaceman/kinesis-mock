import LibraryDependencies._

organization := "io.github.etspaceman"
name := "kinesis-mock"
description := "A Mock API for AWS Kinesis using FS2 and Http4s"
scalaVersion := "2.13.4"
libraryDependencies ++= Seq(
  Http4s.blazeServer,
  Http4s.circe,
  Http4s.dsl,
  Circe.core,
  Circe.derivation,
  Ciris.core,
  Ciris.enumeratum,
  Enumeratum.core,
  Enumeratum.circe,
  JaxbApi,
  MUnit % Test,
  MUnitCatsEffect2 % Test,
  Logback
)
semanticdbEnabled := true
semanticdbVersion := "4.4.2"
ThisBuild / scalafixDependencies += OrganizeImports
scalacOptions ++= ScalacSettings.settings
scalacOptions in (Compile, console) ~= {
  _.filterNot(Set("-Ywarn-unused-import", "-Ywarn-unused:imports"))
}
addCompilerPlugin(KindProjector cross CrossVersion.full)
addCompilerPlugin(BetterMonadicFor)
testFrameworks += new TestFramework("munit.Framework")
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
