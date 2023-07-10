import LibraryDependencies._

lazy val `kinesis-mock` = projectMatrix
  .enablePlugins(DockerImagePlugin, DockerComposePlugin, NoPublishPlugin)
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
      Enumeratum.cats.value,
      Enumeratum.core.value,
      Enumeratum.circe.value,
      Http4s.emberServer.value,
      Http4s.circe.value,
      Http4s.dsl.value,
      Log4Cats.core.value,
      Ciris.core.value,
      FS2.core.value,
      ScodecBits.value
    ),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class", _ @_*) => MergeStrategy.discard
      case x => MergeStrategy.defaultMergeStrategy(x)
    }
  )
  .settings(DockerImagePlugin.settings)
  .jvmPlatform(Seq(Scala213))
  .jsPlatform(Seq(Scala213))

lazy val testkit = projectMatrix
  .enablePlugins(NoPublishPlugin)
  .settings(libraryDependencies ++= testDependencies.value)
  .jvmPlatform(Seq(Scala213))
  .jsPlatform(Seq(Scala213))
  .dependsOn(`kinesis-mock`)

lazy val `unit-tests` = projectMatrix
  .enablePlugins(NoPublishPlugin)
  .jvmPlatform(Seq(Scala213))
  .jsPlatform(Seq(Scala213))
  .dependsOn(testkit % Test)

lazy val `integration-tests` = projectMatrix
  .enablePlugins(NoPublishPlugin, DockerImagePlugin)
  .settings(DockerImagePlugin.settings)
  .settings(
    libraryDependencies ++= Seq(
      Aws.kinesis % Test,
      Aws.kpl % Test,
      Aws.kcl % Test,
      Log4Cats.slf4j % Test
    ),
    Test / parallelExecution := false
  )
  .jvmPlatform(Seq(Scala213))
  .dependsOn(testkit % Test)

lazy val allProjects = Seq(
  `kinesis-mock`,
  testkit,
  `unit-tests`,
  `integration-tests`
)

lazy val functionalTestProjects = List(`integration-tests`).map(_.jvm(Scala213))

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
        Seq(VirtualAxis.jvm, VirtualAxis.ScalaVersionAxis(Scala213, "2.13"))
      ).map(_.project)
    ): _*
  )

lazy val `root-js-213` = project
  .enablePlugins(NoPublishPlugin)
  .settings(commonRootSettings)
  .aggregate(
    allProjects.flatMap(
      _.filterProjects(
        Seq(VirtualAxis.js, VirtualAxis.ScalaVersionAxis(Scala213, "2.13"))
      ).map(_.project)
    ): _*
  )

lazy val rootProjects = List(
  `root-jvm-213`,
  `root-js-213`
).map(_.id)

ThisBuild / githubWorkflowBuildMatrixAdditions += "project" -> rootProjects
