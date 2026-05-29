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
  import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
  import sbtheader.HeaderPlugin.autoImport._
  import scalafix.sbt.ScalafixPlugin.autoImport._

  private val primaryJavaOSCond = Def.setting {
    val java = githubWorkflowJavaVersions.value.head
    val os = githubWorkflowOSes.value.head
    s"matrix.java == '${java.render}' && matrix.os == '${os}'"
  }

  // JS-only steps gated to the JS matrix bucket: JS link, docker compose lifecycle,
  // and the JVM integration tests (which run against the docker-hosted JS-built mock).
  // matrix.project is auto-populated by tlCrossRootProject using the platform
  // aggregate names (kinesis-mock-rootJS / kinesis-mock-rootJVM).
  private val scalaJsCond = Def.setting {
    val java = githubWorkflowJavaVersions.value.head
    val os = githubWorkflowOSes.value.head
    s"matrix.java == '${java.render}' && matrix.os == '${os}' && matrix.project == 'kinesis-mock-rootJS'"
  }

  private val onlyReleases = Def.setting {
    val publicationCond =
      Seq(RefPredicate.StartsWith(Ref.Tag("v")))
        .map(compileBranchPredicate("github.ref", _))
        .mkString("(", " || ", ")")

    s"github.event_name != 'pull_request' && $publicationCond"
  }

  override def buildSettings: Seq[Setting[?]] = Seq(
    tlBaseVersion := "0.6",
    tlCiScalafixCheck := true,
    tlCiHeaderCheck := true,
    tlCiScalafmtCheck := true,
    organization := "io.github.etspaceman",
    startYear := Some(2021),
    licenses := Seq(License.MIT),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    crossScalaVersions := Seq(Scala3),
    scalaVersion := Scala3,
    resolvers += "s01 snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots/",
    tlCiStewardValidateConfig :=
      Some(file(".scala-steward.conf")).filter(_.exists()),
    mergifyStewardConfig := Some(
      MergifyStewardConfig(
        action = MergifyAction.Merge(method = Some("squash")),
        author = "etspaceman-scala-steward-app[bot]"
      )
    ),
    mergifyLabelPaths ++= Map(
      "kinesis-mock" -> file("kinesis-mock/src")
    ),
    githubWorkflowTargetTags += "v*",
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21")),
    githubWorkflowBuildMatrixFailFast := Some(false),
    githubWorkflowBuildMatrixAdditions ++= Map(
      "cbor_enabled" -> List("true", "false"),
      "service_port" -> List("4567", "4568")
    ),
    githubWorkflowBuild ++= {
      val jsCond = scalaJsCond.value
      List(
        WorkflowStep.Sbt(
          List("fastLinkJS"),
          name = Some("Link JS"),
          cond = Some(jsCond)
        ),
        WorkflowStep.Sbt(
          List("Test/fastLinkJS"),
          name = Some("Link Test JS"),
          cond = Some(jsCond)
        ),
        WorkflowStep.Sbt(
          List("dockerComposeUp"),
          name = Some("Docker Compose Up"),
          cond = Some(jsCond),
          env = Map(
            "LOCALSTACK_AUTH_TOKEN" -> "${{ secrets.LOCALSTACK_AUTH_TOKEN }}"
          )
        ),
        WorkflowStep.Sbt(
          commands = List("project kinesis-mock-rootJVM", "itTest"),
          name = Some("Integration Tests"),
          cond = Some(jsCond),
          preamble = false,
          env = Map(
            "CBOR_ENABLED" -> "${{ matrix.cbor_enabled }}",
            "SERVICE_PORT" -> "${{ matrix.service_port }}"
          )
        ),
        WorkflowStep.Sbt(
          List("dockerComposePs", "dockerComposeLogs"),
          name = Some("Print docker logs and container listing"),
          cond = Some("${{ failure() }}"),
          preamble = false
        ),
        WorkflowStep.Sbt(
          List("dockerComposeDown"),
          name = Some("Remove docker containers"),
          cond = Some(jsCond)
        )
      )
    },
    githubWorkflowAddedJobs ++= List(
      WorkflowJob(
        "publishDocker",
        "Publish Docker Image",
        githubWorkflowJobSetup.value.toList ++
          List(
            WorkflowStep.Sbt(
              List("Test/compile"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("fullLinkJS"),
              name = Some("Link JS"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("docker", "setup-buildx-action", "v3"),
              name = Some("Set up Docker Buildx"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Run(
              List(
                "echo ${{ secrets.CR_PAT }} | docker login ghcr.io -u $GITHUB_ACTOR --password-stdin"
              ),
              name = Some("Login to registry"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("buildDockerImage"),
              name = Some("Build Docker Image"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("pushDockerImage"),
              name = Some("Push to registry"),
              cond = Some(primaryJavaOSCond.value)
            )
          ),
        sbtStepPreamble =
          List("++ ${{ matrix.scala }}", "project kinesis-mock-rootJS"),
        scalas = githubWorkflowScalaVersions.value.toList,
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
              List("Test/compile"),
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
        sbtStepPreamble =
          List("++ ${{ matrix.scala }}", "project kinesis-mock-rootJS"),
        scalas = githubWorkflowScalaVersions.value.toList,
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
              List("Test/compile"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("fullLinkJS"),
              name = Some("Link JS"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "setup-node", "v4"),
              name = Some("Setup Node"),
              params = Map(
                "node-version" -> "24",
                "registry-url" -> "https://registry.npmjs.org"
              ),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Run(
              List("npm install -g npm@latest"),
              name = Some("Upgrade npm (>=11.5.1 for trusted publishing)"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("npmPackageInstall"),
              name = Some("Install artifacts to NPM"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("Compile/npmPackage"),
              name = Some("Build NPM package"),
              cond = Some(onlyReleases.value)
            ),
            WorkflowStep.Run(
              List(
                "NPM_DIR=$(find . -type f -name package.json -path '*/npm-package/package.json' -not -path '*/node_modules/*' | head -1 | xargs dirname)",
                "echo \"Publishing from $NPM_DIR\"",
                "cd \"$NPM_DIR\"",
                "npm publish --provenance --access public"
              ),
              name = Some(
                "Publish artifacts to NPM (trusted publishing + provenance)"
              ),
              cond = Some(onlyReleases.value)
            )
          ),
        sbtStepPreamble =
          List("++ ${{ matrix.scala }}", "project kinesis-mock-rootJS"),
        scalas = githubWorkflowScalaVersions.value.toList,
        javas = githubWorkflowJavaVersions.value.toList,
        needs = List("build"),
        permissions = Some(
          Permissions.Specify.defaultRestrictive.withIdToken(
            PermissionValue.Write
          )
        )
      ),
      WorkflowJob(
        "publishAssembly",
        "Publish Fat JAR",
        githubWorkflowJobSetup.value.toList ++
          List(
            WorkflowStep.Sbt(
              List("Test/compile"),
              name = Some("Compile"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Sbt(
              List("assembly"),
              name = Some("Assembly"),
              cond = Some(primaryJavaOSCond.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("bruceadams", "get-release", "v1.3.2"),
              name = Some("Get upload url for release"),
              id = Some("get_release"),
              cond = Some(onlyReleases.value)
            ),
            WorkflowStep.Use(
              UseRef.Public("actions", "upload-release-asset", "v1"),
              name = Some("Upload kinesis-mock.jar"),
              params = Map(
                "upload_url" -> "${{ steps.get_release.outputs.upload_url }}",
                "asset_path" -> "./docker/image/lib/kinesis-mock.jar",
                "asset_name" -> "kinesis-mock.jar",
                "asset_content_type" -> "application/java-archive"
              ),
              cond = Some(onlyReleases.value)
            )
          ),
        sbtStepPreamble =
          List("++ ${{ matrix.scala }}", "project kinesis-mock-rootJVM"),
        scalas = githubWorkflowScalaVersions.value.toList,
        javas = githubWorkflowJavaVersions.value.toList,
        needs = List("build")
      )
    )
  )

  override def projectSettings: Seq[Setting[?]] = Seq(
    scalacOptions ++= ScalacSettings.settings,
    Test / testOptions ++=
      List(
        Tests.Argument(TestFrameworks.MUnit, "+l"),
        Tests.Argument(TestFrameworks.MUnit, "--exclude-tags=integration")
      ),
    libraryDependencies ++= testDependencies.value.map(_ % Test),
    headerLicense := Some(
      HeaderLicense.ALv2(s"${startYear.value.get}-2026", organizationName.value)
    ),
    tlJdkRelease := Some(21)
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
    ),
    addCommandAlias(
      "itTest",
      ";project kinesis-mockJVM;set Test / testOptions := Seq(Tests.Argument(TestFrameworks.MUnit, \"--include-tags=integration\"));Test / testOnly;set Test / testOptions := (Test / testOptions).value;project kinesis-mock-rootJVM"
    )
  ).flatten
}

object KinesisMockPluginKeys {
  def void(a: Any*): Unit = (a, ())._2

  lazy val npmExtraFiles: SettingKey[Seq[File]] =
    settingKey[Seq[File]]("Extra files to copy to the NPM install directory")
  lazy val npmCopyExtraFiles =
    taskKey[Unit]("Copy extra files to the NPM install directory")

  val Scala3 = "3.3.7"

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
