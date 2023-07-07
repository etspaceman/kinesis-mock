import LibraryDependencies._

lazy val `kinesis-mock` = projectMatrix
  .enablePlugins(DockerImagePlugin, DockerComposePlugin, NoPublishPlugin)
  .settings(
    description := "A Mock API for AWS Kinesis",
    libraryDependencies ++= Seq(
      Aws.utils,
      Borer.circe,
      Borer.core,
      Cats.core,
      Cats.effect,
      CatsRetry,
      Circe.core,
      Circe.parser,
      Circe.jackson,
      PureConfig.core,
      PureConfig.enumeratum,
      Enumeratum.cats,
      Enumeratum.core,
      Enumeratum.circe,
      Http4s.emberServer,
      Http4s.circe,
      Http4s.dsl,
      JaxbApi,
      Logback,
      Log4Cats.slf4j,
      OsLib,
      PureConfig.catsEffect,
      PureConfig.core,
      PureConfig.enumeratum,
      UUIDCreator
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

lazy val `integration-tests` = projectMatrix
  .enablePlugins(NoPublishPlugin, DockerImagePlugin)
  .settings(DockerImagePlugin.settings)
  .settings(
    libraryDependencies ++= Seq(
      Aws.kinesis % Test,
      Aws.kpl % Test,
      Aws.kcl % Test
    ),
    Test / parallelExecution := false
  )
  .jvmPlatform(Seq(Scala213))
  .dependsOn(`kinesis-mock` % Test)

lazy val allProjects = Seq(
  `kinesis-mock`,
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
