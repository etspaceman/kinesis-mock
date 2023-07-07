import LibraryDependencies._
import org.typelevel.sbt._
import org.typelevel.sbt.gha._
import org.typelevel.sbt.mergify._
import sbt.AutoPlugin
import sbt.Keys._
import sbt._

object KinesisMockPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = TypelevelPlugin

  def mkCommand(commands: List[String]): String =
    commands.mkString("; ", "; ", "")

  val autoImport: KinesisMockPluginKeys.type = KinesisMockPluginKeys

  import TypelevelCiPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._
  import TypelevelVersioningPlugin.autoImport._
  import autoImport._
  import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
  import scalafix.sbt.ScalafixPlugin.autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    tlBaseVersion := "0.3",
    tlCiScalafixCheck := true,
    tlJdkRelease := Some(17),
    tlCiMimaBinaryIssueCheck := false,
    organization := "io.github.etspaceman",
    startYear := Some(2021),
    licenses := Seq(License.MIT),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    crossScalaVersions := Seq(Scala213),
    scalaVersion := Scala213
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    scalacOptions ++= ScalacSettings.settings,
    Compile / console / scalacOptions ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Ywarn-unused:imports"))
    },
    addCompilerPlugin(KindProjector cross CrossVersion.full),
    addCompilerPlugin(BetterMonadicFor),
    Test / testOptions ++= {
      List(Tests.Argument(TestFrameworks.MUnit, "+l"))
    },
    libraryDependencies ++= Seq(
      Enumeratum.scalacheck % Test,
      Munit.core % Test,
      Munit.catsEffect2 % Test,
      Munit.scalacheck % Test,
      Munit.scalacheckEffect % Test,
      Refined.scalacheck % Test,
      ScalacheckGenRegexp % Test
    ),
    headerLicense := Some(
      HeaderLicense.ALv2(s"${startYear.value.get}-2023", organizationName.value)
    )
  ) ++ Seq(
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
      "validate",
      ";Fun / dockerComposeTestQuick;prettyCheck"
    )
  ).flatten
}

object KinesisMockPluginKeys {
  val Scala213 = "2.13.11"
}
