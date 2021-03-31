package kinesis.mock

import scala.concurrent.ExecutionContext

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._

import kinesis.mock.cache.{Cache, CacheConfig}

object KinesisMockService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
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
        res <- server.use(_ => IO.never).as(ExitCode.Success)
      } yield res
    )
}
