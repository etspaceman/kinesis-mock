import scala.sys.process._

import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

object DockerImagePlugin extends AutoPlugin {
  override def trigger = noTrigger

  val autoImport: DockerImagePluginKeys.type = DockerImagePluginKeys
  import autoImport._
  import sbtassembly.AssemblyPlugin.autoImport._

  val dockerTagTask: Def.Initialize[Task[String]] = Def.task {
    s"${dockerRepository.value}/${dockerNamespace.value}/${name.value}:${imageTag.value}"
  }

  val buildDockerImageTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd =
      s"""docker build \\
         |  --build-arg DOCKER_SERVICE_FILE=${serviceFileLocation.value}${serviceFileName.value} \\
         |  -f ${dockerfileLocation.value + dockerfile.value} \\
         |  -t ${dockerTagTask.value} \\
         |  .""".stripMargin
    log.info(s"Running $cmd")

    val res = cmd.replace("\\", "").!
    if (res != 0)
      throw new IllegalStateException(s"docker build returned $res")
  }

  val pushDockerImageTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd = s"""docker push ${dockerTagTask.value}"""

    log.info(s"Running $cmd")
    val res = cmd.!
    if (res != 0)
      throw new IllegalStateException(s"docker build returned $res")
  }

  def settings: Seq[Setting[_]] =
    Seq(
      buildDockerImage := buildDockerImageTask.value,
      pushDockerImage := pushDockerImageTask.value,
      imageTag := (ThisBuild / version).value,
      dockerRepository := "ghcr.io",
      dockerNamespace := "etspaceman",
      serviceFileLocation := "docker/image/lib/",
      serviceFileName := "main.js",
      dockerfileLocation := "docker/",
      dockerfile := sys.env.getOrElse("KINESIS_MOCK_DOCKERFILE", "Dockerfile")
    )
}

object DockerImagePluginKeys {
  val imageTag = settingKey[String]("Tag for the image, e.g. latest")
  val dockerRepository = settingKey[String](
    "Repository for the docker images, e.g ghcr.io"
  )
  val dockerNamespace =
    settingKey[String]("namespace to append to the tag, e.g etspaceman")

  val serviceFileLocation = settingKey[String](
    "Location where the application file exists, e.g. docker/image/lib/"
  )
    val serviceFileName= settingKey[String](
    "Name of the application file, e.g. main.js"
  )
  val dockerfileLocation =
    settingKey[String]("Location of the Dockerfile, e.g. docker/")
  val dockerfile = settingKey[String]("Dockerfile to use, e.g. Dockerfile")
  val buildDockerImage =
    taskKey[Unit]("Builds the docker images defined in the project.")
  val pushDockerImage =
    taskKey[Unit]("Pushes the docker image tag defined in the project.")
}
