package kinesis.mock

import scala.concurrent.ExecutionContext

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import io.circe.syntax._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.typelevel.log4cats.slf4j.Slf4jLogger

import kinesis.mock.cache.{Cache, CacheConfig}

object KinesisMockService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use(blocker =>
      for {
        logger <- Slf4jLogger.create[IO]
        cacheConfig <- CacheConfig.read(blocker)
        _ <- logger.info(Map("cacheConfig" -> cacheConfig.asJson.noSpaces))(
          "Logging Cache Config"
        )
        cache <- Cache(cacheConfig)
        serviceConfig <- KinesisMockServiceConfig.read(blocker)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        context <- ssl.loadContextFromClasspath[IO](
          serviceConfig.keyStorePassword,
          serviceConfig.keyManagerPassword
        )
        server = BlazeServerBuilder[IO](ExecutionContext.global)
          .bindHttp(serviceConfig.port)
          .withHttpApp(app)
          .withSslContext(context)
          .enableHttp2(true)
          .resource
        _ <- logger.info(
          s"Starting Kinesis Mock Service on port ${serviceConfig.port}"
        )
        res <- server.use(_ => IO.never).as(ExitCode.Success)
      } yield res
    )
}
