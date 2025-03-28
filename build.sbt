import java.nio.file.Files
import java.nio.file.StandardCopyOption

import LibraryDependencies._
import org.scalajs.linker.interface.ESVersion
import org.scalajs.sbtplugin.Stage

lazy val `kinesis-mock` = projectMatrix
  .enablePlugins(DockerImagePlugin, NoPublishPlugin)
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
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class", _ @_*) => MergeStrategy.discard
      case x => MergeStrategy.defaultMergeStrategy(x)
    },
    assembly / assemblyOutputPath := file(
      s"docker/image/lib/${name.value}.jar"
    ),
    Compile / mainClass := Some("kinesis.mock.KinesisMockService")
  )
  .settings(DockerImagePlugin.settings)
  .jvmPlatform(Seq(Scala3))
  .jsPlatform(Seq(Scala3))

lazy val `kinesis-mock-js` =
  `kinesis-mock`
    .js(Scala3)
    .enablePlugins(NpmPackagePlugin)
    .settings(
      Compile / fastLinkJS / scalaJSLinkerOutputDirectory := file(
        "docker/image/lib"
      ),
      Compile / fullLinkJS / scalaJSLinkerOutputDirectory := file(
        "docker/image/lib"
      ),
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
      scalaJSLinkerConfig ~= {
        _.withESFeatures(_.withESVersion(ESVersion.ES2018))
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
        file("kinesis-mock/src/main/resources/server.json")
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

lazy val testkit = projectMatrix
  .enablePlugins(NoPublishPlugin)
  .settings(libraryDependencies ++= testDependencies.value)
  .jvmPlatform(Seq(Scala3))
  .jsPlatform(Seq(Scala3))
  .dependsOn(`kinesis-mock`)

lazy val `testkit-js` = testkit
  .js(Scala3)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    scalaJSLinkerConfig ~= {
      _.withESFeatures(_.withESVersion(ESVersion.ES2018))
    }
  )

lazy val `unit-tests` = projectMatrix
  .enablePlugins(NoPublishPlugin)
  .jvmPlatform(Seq(Scala3))
  .jsPlatform(Seq(Scala3))
  .dependsOn(testkit % Test)

lazy val `unit-tests-js` = `unit-tests`
  .js(Scala3)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    scalaJSLinkerConfig ~= {
      _.withESFeatures(_.withESVersion(ESVersion.ES2018))
    }
  )

lazy val `integration-tests` = projectMatrix
  .enablePlugins(NoPublishPlugin)
  .settings(
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
  .jvmPlatform(Seq(Scala3))
  .dependsOn(testkit % Test)

lazy val allProjects = Seq(
  `kinesis-mock`,
  testkit,
  `unit-tests`,
  `integration-tests`
)

lazy val functionalTestProjects = List(`kinesis-mock`).map(_.js(Scala3))

def commonRootSettings: Seq[Setting[_]] =
  DockerComposePlugin.settings(true, functionalTestProjects) ++ Seq(
    name := "kinesis-mock-root",
    ThisBuild / mergifyLabelPaths ++= allProjects.map { x =>
      x.id -> x.base
    }.toMap
  )

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(commonRootSettings)
  .aggregate(allProjects.flatMap(_.projectRefs): _*)

lazy val `root-jvm-213` = project
  .enablePlugins(NoPublishPlugin)
  .settings(commonRootSettings)
  .aggregate(
    allProjects.flatMap(
      _.filterProjects(
        Seq(VirtualAxis.jvm, VirtualAxis.ScalaVersionAxis(Scala3, "2.13"))
      ).map(_.project)
    ): _*
  )

lazy val `root-js-213` = project
  .enablePlugins(NoPublishPlugin)
  .settings(commonRootSettings)
  .aggregate(
    allProjects.flatMap(
      _.filterProjects(
        Seq(VirtualAxis.js, VirtualAxis.ScalaVersionAxis(Scala3, "2.13"))
      ).map(_.project)
    ): _*
  )

lazy val rootProjects = List(
  `root-jvm-213`,
  `root-js-213`
).map(_.id)

ThisBuild / githubWorkflowBuildMatrixAdditions += "project" -> rootProjects
