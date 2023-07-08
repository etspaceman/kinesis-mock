import LibraryDependencies._
import org.typelevel.sbt._
import org.typelevel.sbt.gha._
import org.typelevel.sbt.mergify._
import sbt.AutoPlugin
import sbt.Keys._
import sbt._

object KinesisMockPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins = TypelevelCiPlugin && TypelevelSettingsPlugin && TypelevelVersioningPlugin

  def mkCommand(commands: List[String]): String =
    commands.mkString("; ", "; ", "")

  val autoImport: KinesisMockPluginKeys.type = KinesisMockPluginKeys

  import GenerativePlugin.autoImport._
  import TypelevelCiPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._
  import TypelevelVersioningPlugin.autoImport._
  import autoImport._
  import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
  import scalafix.sbt.ScalafixPlugin.autoImport._

  private val primaryJavaOSCond = Def.setting {
    val java = githubWorkflowJavaVersions.value.head
    val os = githubWorkflowOSes.value.head
    s"matrix.java == '${java.render}' && matrix.os == '${os}'"
  }

  private val onlyScalaJsCond = Def.setting {
    primaryJavaOSCond.value + s" && startsWith(matrix.project, 'root-js')"
  }

  private val onlyFailures = Def.setting {
    "${{ failure() }}"
  }

  override def buildSettings: Seq[Setting[_]] = Seq(
    tlBaseVersion := "0.3",
    tlCiScalafixCheck := true,
    tlJdkRelease := Some(17),
    tlCiMimaBinaryIssueCheck := false,
    tlCiDocCheck := false,
    organization := "io.github.etspaceman",
    startYear := Some(2021),
    licenses := Seq(License.MIT),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    crossScalaVersions := Seq(Scala213),
    scalaVersion := Scala213,
    githubWorkflowBuildMatrixFailFast := Some(false),
    githubWorkflowBuildMatrixAdditions := Map(
      "cbor_enabled" -> List("true", "false"),
      "service_port" -> List("4567", "4568")
    ),
    githubWorkflowBuild := {
      val style = (tlCiHeaderCheck.value, tlCiScalafmtCheck.value) match {
        case (true, true) => // headers + formatting
          List(
            WorkflowStep.Sbt(
              List(
                "headerCheckAll",
                "fmtCheck"
              ),
              name = Some("Check headers and formatting"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        case (true, false) => // headers
          List(
            WorkflowStep.Sbt(
              List("headerCheckAll"),
              name = Some("Check headers"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        case (false, true) => // formatting
          List(
            WorkflowStep.Sbt(
              List("fmtCheck"),
              name = Some("Check formatting"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        case (false, false) => Nil // nada
      }

      val test = List(
        WorkflowStep.Use(
          UseRef.Public("nick-fields", "retry", "v2"),
          name = Some("Docker Compose Up"),
          cond = Some(primaryJavaOSCond.value),
          params = Map(
            "timeout_minutes" -> "15",
            "max_attempts" -> "3",
            "command" -> "sbt 'project ${{ matrix.project }}' dockerComposeUp",
            "retry_on" -> "error",
            "on_retry_command" -> "sbt 'project ${{ matrix.project }}' dockerComposeDown"
          )
        ),
        WorkflowStep.Sbt(
          List("Test/fastLinkJS"),
          name = Some("Link JS"),
          cond = Some(onlyScalaJsCond.value)
        ),
        WorkflowStep.Sbt(
          List(
            "test"
          ),
          name = Some("Test"),
          cond = Some(primaryJavaOSCond.value)
        ),
        WorkflowStep.Sbt(
          List(
            "dockerComposePs",
            "dockerComposeLogs"
          ),
          name = Some("Print docker logs and container listing"),
          cond = Some(onlyFailures.value)
        ),
        WorkflowStep.Sbt(
          List(
            "dockerComposeDown"
          ),
          name = Some("Remove docker containers"),
          cond = Some(primaryJavaOSCond.value)
        )
      )

      val scalafix =
        if (tlCiScalafixCheck.value)
          List(
            WorkflowStep.Sbt(
              List("fixCheck"),
              name = Some("Check scalafix lints"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        else Nil

      val mima =
        if (tlCiMimaBinaryIssueCheck.value)
          List(
            WorkflowStep.Sbt(
              List("mimaReportBinaryIssues"),
              name = Some("Check binary compatibility"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        else Nil

      val doc =
        if (tlCiDocCheck.value)
          List(
            WorkflowStep.Sbt(
              List("doc"),
              name = Some("Generate API documentation"),
              cond = Some(primaryJavaOSCond.value)
            )
          )
        else Nil

      style ++ test ++ scalafix ++ mima ++ doc
    }
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
      Enumeratum.scalacheck.value % Test,
      Munit.core.value % Test,
      Munit.catsEffect.value % Test,
      Munit.scalacheck.value % Test,
      Munit.scalacheckEffect.value % Test,
      Refined.scalacheck.value % Test
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
    )
  ).flatten
}

object KinesisMockPluginKeys {
  val Scala213 = "2.13.11"
}
