import scala.sys.process._

import sbt.Keys._
import sbt._

object DockerComposePlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires: Plugins = DockerImagePlugin

  val autoImport: DockerComposePluginKeys.type = DockerComposePluginKeys
  import autoImport._

  val createNetworkTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd = s"docker network create ${networkName.value}"
    log.info(s"Running $cmd")
    val res = cmd.!
    if (res != 0)
      log.warn(s"docker network create returned $res. Ignoring and proceeding.")
  }

  val removeNetworkTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd = s"docker network rm ${networkName.value}"
    log.info(s"Running $cmd")
    val res = cmd.!
    if (res != 0)
      log.warn(s"docker network create returned $res. Ignoring and proceeding.")
  }

  val composeFile: Def.Initialize[Task[String]] =
    Def.task(s"${composeFileLocation.value}docker-compose.yml")

  val dockerComposeUpBaseTask: Def.Initialize[Task[Unit]] = Def
    .task {
      val log = sbt.Keys.streams.value.log
      val cmd =
        s"docker-compose -f ${composeFile.value} up -d "
      log.info(s"Running $cmd")
      val res = Process(
        cmd,
        None,
        "DOCKER_TAG_VERSION" -> (ThisBuild / version).value,
        "DOCKER_NET_NAME" -> networkName.value,
        "COMPOSE_PROJECT_NAME" -> composeProjectName.value
      ).!
      if (res != 0)
        throw new IllegalStateException(s"docker-compose up returned $res")
    }
    .dependsOn(createNetworkTask)

  val dockerComposeUpTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    if (buildImage.value) {
      dockerComposeUpBaseTask.dependsOn(
        DockerImagePlugin.packageAndBuildDockerImageTask
      )
    } else dockerComposeUpBaseTask
  }

  val dockerComposeKillTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd = s"docker-compose -f ${composeFile.value} kill -s 9"
    log.info(s"Running $cmd")
    val res = Process(
      cmd,
      None,
      "DOCKER_TAG_VERSION" -> (ThisBuild / version).value,
      "DOCKER_NET_NAME" -> networkName.value,
      "COMPOSE_PROJECT_NAME" -> composeProjectName.value
    ).!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose kill returned $res")
  }

  val dockerComposeDownBaseTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd = s"docker-compose -f ${composeFile.value} down -v"
    log.info(s"Running $cmd")
    val res = Process(
      cmd,
      None,
      "DOCKER_TAG_VERSION" -> (ThisBuild / version).value,
      "DOCKER_NET_NAME" -> networkName.value,
      "COMPOSE_PROJECT_NAME" -> composeProjectName.value
    ).!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose down returned $res")
  }

  val dockerComposeDownTask: Def.Initialize[Task[Unit]] =
    Def.sequential(
      dockerComposeKillTask,
      dockerComposeDownBaseTask,
      removeNetworkTask
    )

  val dockerComposeLogsTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd =
      s"docker-compose -f ${composeFile.value} logs"
    log.info(s"Running $cmd")
    val res = Process(
      cmd,
      None,
      "DOCKER_TAG_VERSION" -> (ThisBuild / version).value,
      "DOCKER_NET_NAME" -> networkName.value,
      "COMPOSE_PROJECT_NAME" -> composeProjectName.value
    ).!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose logs returned $res")
  }

  val dockerComposePsTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sbt.Keys.streams.value.log
    val cmd =
      s"docker-compose -f ${composeFile.value} ps -a"
    log.info(s"Running $cmd")
    val res = Process(
      cmd,
      None,
      "DOCKER_TAG_VERSION" -> (ThisBuild / version).value,
      "DOCKER_NET_NAME" -> networkName.value,
      "COMPOSE_PROJECT_NAME" -> composeProjectName.value
    ).!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose ps -a returned $res")
  }

  def dockerComposeTestQuickTask(
      configuration: Configuration
  ): Def.Initialize[Task[Unit]] =
    Def.sequential(
      dockerComposeUp,
      configuration / test,
      dockerComposeDown
    )

  def settings(configuration: Configuration): Seq[Setting[_]] =
    Seq(
      createNetwork := createNetworkTask.value,
      removeNetwork := removeNetworkTask.value,
      dockerComposeUp := dockerComposeUpTask.value,
      dockerComposeDown := dockerComposeDownTask.value,
      dockerComposeLogs := dockerComposeLogsTask.value,
      dockerComposePs := dockerComposePsTask.value,
      dockerComposeTestQuick := dockerComposeTestQuickTask(configuration).value,
      composeFileLocation := "docker/",
      networkName := sys.env
        .getOrElse("DOCKER_NET_NAME", "kinesis_mock_network"),
      composeProjectName := sys.env
        .getOrElse("COMPOSE_PROJECT_NAME", "kinesis-mock"),
      buildImage := true
    )

}

object DockerComposePluginKeys {

  val FunctionalTest = config("fun").extend(Test)

  val composeFileLocation =
    settingKey[String]("Path to docker-compose files, e.g. docker/")
  val networkName = settingKey[String]("Name of network to create")
  val composeProjectName =
    settingKey[String]("Name of project for docker-compose.")
  val buildImage = settingKey[Boolean](
    "Determines if dockerComposeUp should also build a docker image via the DockerImagePlugin"
  )

  val createNetwork = taskKey[Unit]("Creates a docker network")
  val removeNetwork = taskKey[Unit]("Removes a docker network")
  val dockerComposeTestQuick =
    taskKey[Unit]("Brings up docker, runs 'test', brings down docker")
  val dockerComposeUp =
    taskKey[Unit](
      "Builds the images and then runs `docker-compose -f <file> up -d` for the scope"
    )
  val dockerComposeDown =
    taskKey[Unit]("Runs `docker-compose -f <file> down` for the scope")
  val dockerComposeLogs =
    taskKey[Unit]("Runs `docker-compose -f <file> logs` for the scope")
  val dockerComposePs =
    taskKey[Unit]("Runs `docker-compose -f <file> ps -a` for the scope")
}
