import LibraryDependencies._
import org.typelevel.sbt._
import org.typelevel.sbt.gha._
import org.typelevel.sbt.mergify._
import sbt.AutoPlugin
import sbt.Keys._
import sbt._

object KinesisMockPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires: Plugins =
    TypelevelCiPlugin && TypelevelSettingsPlugin && TypelevelVersioningPlugin

  def mkCommand(commands: List[String]): String =
    commands.mkString("; ", "; ", "")

  val autoImport: KinesisMockPluginKeys.type = KinesisMockPluginKeys

  import GenerativePlugin.autoImport._
  import MergifyPlugin.autoImport._
  import TypelevelCiPlugin.autoImport._
  import TypelevelSettingsPlugin.autoImport._
  import TypelevelVersioningPlugin.autoImport._
  import _root_.io.chrisdavenport.npmpackage.sbtplugin.NpmPackagePlugin.autoImport._
  import autoImport._
  import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
  import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
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

  private val onlyReleases = Def.setting {
    val publicationCond =
      Seq(RefPredicate.StartsWith(Ref.Tag("v")))
        .map(compileBranchPredicate("github.ref", _))
        .mkString("(", " || ", ")")

    s"github.event_name != 'pull_request' && $publicationCond"
  }

  override def buildSettings: Seq[Setting[_]] = Seq(
    tlBaseVersion := "0.4",
    tlCiScalafixCheck := true,
    tlCiMimaBinaryIssueCheck := false,
    tlCiDocCheck := false,
    organization := "io.github.etspaceman",
    startYear := Some(2021),
    licenses := Seq(License.MIT),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    crossScalaVersions := Seq(Scala213),
    scalaVersion := Scala213,
    resolvers += "s01 snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots/",
    tlCiStewardValidateConfig :=
      Some(file(".scala-steward.conf")).filter(_.exists()),
    mergifyStewardConfig := Some(
      MergifyStewardConfig(action =
        MergifyAction.Merge(method = Some("squash"))
      )
    ),
    githubWorkflowTargetTags += "v*",
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    githubWorkflowBuildMatrixFailFast := Some(false),
    githubWorkflowBuildMatrixAdditions := Map(
      "cbor_enabled" -> List("true", "false"),
      "service_port" -> List("4567", "4568")
    ),
    githubWorkflowBuildSbtStepPreamble := Seq(
      s"project $${{ matrix.project }}"
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
        WorkflowStep.Sbt(
          List("cpl"),
          name = Some("Compile"),
          cond = Some(primaryJavaOSCond.value)
        ),
        WorkflowStep.Sbt(
          List("fastLinkJS"),
          name = Some("Link JS"),
          cond = Some(onlyScalaJsCond.value)
        ),
        WorkflowStep.Sbt(
          List("Test/fastLinkJS"),
          name = Some("Link Test JS"),
          cond = Some(onlyScalaJsCond.value)
        ),
        WorkflowStep.Use(
          UseRef.Public("nick-fields", "retry", "v2"),
          name = Some("Unit Tests"),
          cond = Some(primaryJavaOSCond.value),
          params = Map(
            "timeout_minutes" -> "15",
            "max_attempts" -> "3",
            "command" -> "sbt 'project ${{ matrix.project }}' unit-tests/test",
            "retry_on" -> "error"
          )
        )
      )

      val integrationTest = List(
        WorkflowStep.Use(
          UseRef.Public("nick-fields", "retry", "v2"),
          name = Some("Docker Compose Up"),
          cond = Some(onlyScalaJsCond.value),
          params = Map(
            "timeout_minutes" -> "15",
            "max_attempts" -> "3",
            "command" -> "sbt 'project ${{ matrix.project }}' dockerComposeUp",
            "retry_on" -> "error",
            "on_retry_command" -> "sbt 'project ${{ matrix.project }}' dockerComposeDown"
          )
        ),
        WorkflowStep.Use(
          UseRef.Public("nick-fields", "retry", "v2"),
          name = Some("Integration Tests"),
          cond = Some(onlyScalaJsCond.value),
          params = Map(
            "timeout_minutes" -> "15",
            "max_attempts" -> "3",
            "command" -> "sbt 'project ${{ matrix.project }}' integration-tests/test",
            "retry_on" -> "error"
          ),
          env = Map(
            "CBOR_ENABLED" -> "${{ matrix.cbor_enabled }}",
            "SERVICE_PORT" -> "${{ matrix.service_port }}"
          )
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
          cond = Some(onlyScalaJsCond.value)
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

      style ++ test ++ integrationTest ++ scalafix ++ mima ++ doc
    },
    githubWorkflowAddedJobs ++= List(
      WorkflowJob(
        "publishDocker",
        "Publish Docker Image",
        githubWorkflowJobSetup.value.toList ++
          List(
            WorkflowStep.Sbt(
              List("cpl"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("fullLinkJS"),
              name = Some("Link JS"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("kinesis-mockJS/buildDockerImage"),
              name = Some("Build Docker Image"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Run(
              List(
                "echo ${{ secrets.CR_PAT }} | docker login ghcr.io -u $GITHUB_ACTOR --password-stdin"
              ),
              name = Some("Login to registry"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Run(
              List(
                "VERSION=${{ github.ref_name }}",
                """echo "VERSION=${VERSION:1}" >> $GITHUB_ENV"""
              ),
              name = Some("Get version"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Run(
              List(
                """echo "${VERSION}"""",
                """docker push "ghcr.io/etspaceman/kinesis-mock:${VERSION}""""
              ),
              name = Some("Push to registry"),
              cond = Some(primaryJavaOSCond.value)
            )
          ),
        scalas = Nil,
        javas = githubWorkflowJavaVersions.value.toList,
        cond = Some(onlyReleases.value),
        needs = List("build")
      ),
      WorkflowJob(
        "publishJSAssets",
        "Publish JS Assets",
        githubWorkflowJobSetup.value.toList ++
          List(
            WorkflowStep.Sbt(
              List("cpl"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("fullLinkJS"),
              name = Some("Link JS"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("bruceadams", "get-release", "v1.3.2"),
              name = Some("Get upload url for release"),
              id = Some("get_release")
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "upload-release-asset", "v1"),
              name = Some("Upload main.js"),
              params = Map(
                "upload_url" -> "${{ steps.get_release.outputs.upload_url }}",
                "asset_path" -> "./docker/image/lib/main.js",
                "asset_name" -> "main.js",
                "asset_content_type" -> "text/javascript"
              )
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "upload-release-asset", "v1"),
              name = Some("Upload main.js.map"),
              params = Map(
                "upload_url" -> "${{ steps.get_release.outputs.upload_url }}",
                "asset_path" -> "./docker/image/lib/main.js.map",
                "asset_name" -> "main.js.map",
                "asset_content_type" -> "application/json"
              )
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "upload-release-asset", "v1"),
              name = Some("Upload server.json"),
              params = Map(
                "upload_url" -> "${{ steps.get_release.outputs.upload_url }}",
                "asset_path" -> "./kinesis-mock/src/main/resources/server.json",
                "asset_name" -> "server.json",
                "asset_content_type" -> "application/json"
              )
            )
          ),
        scalas = Nil,
        javas = githubWorkflowJavaVersions.value.toList,
        cond = Some(onlyReleases.value),
        needs = List("build")
      ),
      WorkflowJob(
        "publishNPM",
        "Publish To NPM",
        githubWorkflowJobSetup.value.toList ++
          List(
            WorkflowStep.Sbt(
              List("cpl"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("fullLinkJS"),
              name = Some("Link JS"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "setup-node", "v3"),
              name = Some("Setup Node"),
              params = Map(
                "node-version" -> "18"
              ),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("npmPackageInstall"),
              name = Some("Install artifacts to NPM"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("npmPackageNpmrc", "npmPackagePublish"),
              name = Some("Publish artifacts to NPM"),
              cond = Some(onlyReleases.value),
              env = Map(
                "NPM_TOKEN" -> "${{ secrets.NPM_TOKEN }}" // https://docs.npmjs.com/using-private-packages-in-a-ci-cd-workflow#set-the-token-as-an-environment-variable-on-the-cicd-server
              )
            )
          ),
        scalas = Nil,
        javas = githubWorkflowJavaVersions.value.toList,
        needs = List("build")
      )
    ),
    githubWorkflowAddedJobs ++= tlCiStewardValidateConfig.value.toList
      .map { config =>
        WorkflowJob(
          "validate-steward",
          "Validate Steward Config",
          WorkflowStep.Checkout ::
            WorkflowStep.Use(
              UseRef.Public("coursier", "setup-action", "v1"),
              Map("apps" -> "scala-steward")
            ) ::
            WorkflowStep.Run(
              List(s"scala-steward validate-repo-config $config")
            ) :: Nil,
          scalas = List.empty,
          javas = List.empty
        )
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
    libraryDependencies ++= testDependencies.value.map(_ % Test),
    headerLicense := Some(
      HeaderLicense.ALv2(s"${startYear.value.get}-2023", organizationName.value)
    ),
    tlJdkRelease := Some(17)
  ) ++ Seq(
    addCommandAlias("cpl", ";Test / compile"),
    addCommandAlias(
      "fixCheck",
      ";Compile / scalafix --check;Test / scalafix --check"
    ),
    addCommandAlias(
      "fix",
      ";Compile / scalafix;Test / scalafix"
    ),
    addCommandAlias(
      "fmt",
      ";Compile / scalafmt;Test / scalafmt;scalafmtSbt"
    ),
    addCommandAlias(
      "fmtCheck",
      ";Compile / scalafmtCheck;Test / scalafmtCheck;scalafmtSbtCheck"
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
  def void(a: Any*): Unit = (a, ())._2

  lazy val npmExtraFiles: SettingKey[Seq[File]] =
    settingKey[Seq[File]]("Extra files to copy to the NPM install directory")
  lazy val npmCopyExtraFiles =
    taskKey[Unit]("Copy extra files to the NPM install directory")

  val Scala213 = "2.13.11"

  val testDependencies = Def.setting(
    Seq(
      Enumeratum.scalacheck.value,
      Munit.core.value,
      Munit.catsEffect.value,
      Munit.scalacheck.value,
      Munit.scalacheckEffect.value,
      Refined.scalacheck.value,
      ScalaParserCombinators.value
    )
  )

  def compileRef(ref: Ref): String = ref match {
    case Ref.Branch(name) => s"refs/heads/$name"
    case Ref.Tag(name)    => s"refs/tags/$name"
  }

  def compileBranchPredicate(target: String, pred: RefPredicate): String =
    pred match {
      case RefPredicate.Equals(ref) =>
        s"$target == '${compileRef(ref)}'"

      case RefPredicate.Contains(Ref.Tag(name)) =>
        s"(startsWith($target, 'refs/tags/') && contains($target, '$name'))"

      case RefPredicate.Contains(Ref.Branch(name)) =>
        s"(startsWith($target, 'refs/heads/') && contains($target, '$name'))"

      case RefPredicate.StartsWith(ref) =>
        s"startsWith($target, '${compileRef(ref)}')"

      case RefPredicate.EndsWith(Ref.Tag(name)) =>
        s"(startsWith($target, 'refs/tags/') && endsWith($target, '$name'))"

      case RefPredicate.EndsWith(Ref.Branch(name)) =>
        s"(startsWith($target, 'refs/heads/') && endsWith($target, '$name'))"
    }
}
