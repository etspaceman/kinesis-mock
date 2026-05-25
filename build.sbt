import java.nio.file.Files
import java.nio.file.StandardCopyOption

import LibraryDependencies._
import org.scalajs.linker.interface.ESVersion
import org.scalajs.sbtplugin.Stage
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val `kinesis-mock` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .enablePlugins(DockerImagePlugin, NoPublishPlugin)
  .settings(DockerImagePlugin.settings)
  .settings(
    description := "A Mock API for AWS Kinesis",
    libraryDependencies ++= Seq(
      Borer.circe.value,
      Borer.core.value,
      Cats.core.value,
      Cats.effect.value,
      Circe.core.value,
      Circe.parser.value,
      Circe.fs2.value,
      Ciris.core.value,
      Ciris.enumeratum.value,
      Enumeratum.cats.value,
      Enumeratum.core.value,
      Enumeratum.circe.value,
      Http4s.emberServer.value,
      Http4s.circe.value,
      Http4s.dsl.value,
      Log4Cats.core.value,
      FS2.core.value,
      FS2.io.value,
      ScodecBits.value
    ),
    Compile / unmanagedResourceDirectories +=
      (ThisBuild / baseDirectory).value / "src" / "main" / "resources",
    Test / unmanagedResourceDirectories +=
      (ThisBuild / baseDirectory).value / "src" / "test" / "resources"
  )
  .jvmSettings(
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "src" / "main" / "scalajvm",
    Test / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "src" / "test" / "scalajvm",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class", _ @_*) => MergeStrategy.discard
      case x => MergeStrategy.defaultMergeStrategy(x)
    },
    assembly / assemblyOutputPath := file(
      s"docker/image/lib/${name.value}.jar"
    ),
    Compile / mainClass := Some("kinesis.mock.KinesisMockService"),
    libraryDependencies ++= Seq(
      Aws.kinesis % Test,
      Aws.cloudwatch % Test,
      Aws.kpl % Test,
      Aws.kcl % Test,
      Log4Cats.slf4j % Test,
      Logback % Test
    ),
    Test / parallelExecution := false
  )
  .jsConfigure(_.enablePlugins(NpmPackagePlugin))
  .jsSettings(
    Compile / unmanagedSourceDirectories +=
      (ThisBuild / baseDirectory).value / "src" / "main" / "scalajs",
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := file(
      "docker/image/lib"
    ),
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := file(
      "docker/image/lib"
    ),
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    scalaJSLinkerConfig ~= {
      _.withESFeatures(_.withESVersion(ESVersion.ES2021))
    },
    npmPackageName := "kinesis-local",
    npmPackageAuthor := "Eric Meisel",
    npmPackageDescription := description.value,
    npmPackageLicense := Some("MIT"),
    npmPackageRepository := Some(
      "https://github.com/etspaceman/kinesis-mock"
    ),
    npmPackageBinaryEnable := true,
    npmPackageStage := Stage.FullOpt,
    npmPackageKeywords := Seq(
      "kinesis mock",
      "kinesis-mock",
      "kinesis",
      "aws kinesis",
      "aws kinesis mock",
      "aws-kinesis-mock"
    ),
    npmExtraFiles := Seq(
      file("LICENSE"),
      file("src/main/resources/server.json")
    ),
    Compile / npmCopyExtraFiles := Def.task {
      val targetDir = (Compile / npmPackageOutputDirectory).value
      val log = streams.value.log

      if (Files.exists(targetDir.toPath())) ()
      else Files.createDirectories(targetDir.toPath())

      npmExtraFiles.value.foreach { f =>
        val targetPath = (targetDir / f.name).toPath

        Files.copy(
          f.toPath,
          targetPath,
          StandardCopyOption.REPLACE_EXISTING
        )
        log.info(s"Wrote $f to $targetPath")
      }
    }.value,
    Compile / npmPackage := {
      val b = (Compile / npmPackagePackageJson).value
      val a = (Compile / npmPackageOutputJS).value
      val c = (Compile / npmPackageWriteREADME).value
      val d = (Compile / npmCopyExtraFiles).value
      void(a, b, c, d)
    }
  )

lazy val `kinesis-mock-jvm` = `kinesis-mock`.jvm
lazy val `kinesis-mock-js` = `kinesis-mock`.js

lazy val functionalTestProjects: List[Project] = List(`kinesis-mock-js`)

ThisBuild / mergifyLabelPaths ++= Map(
  "kinesis-mock" -> file("src")
)

lazy val `kinesis-mock-root` = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(DockerComposePlugin.settings(true, functionalTestProjects))
  .aggregate(`kinesis-mock-jvm`, `kinesis-mock-js`)

ThisBuild / githubWorkflowBuildMatrixAdditions += "project" -> List(
  "kinesis-mockJVM",
  "kinesis-mockJS"
)
