package kinesis.mock

import scala.concurrent.ExecutionContext

import cats.effect.{ExitCode, IO, IOApp}
import ciris._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._

import kinesis.mock.cache.{Cache, CacheConfig}

object KinesisMockService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      cacheConfig <- CacheConfig.read.load[IO]
      cache <- Cache(cacheConfig)
      port <- env("KINESIS_MOCK_PORT").as[Int].default(4567).load[IO]
      app = new KinesisMockRoutes(cache).routes.orNotFound
      server = BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(port)
        .withHttpApp(app)
        .resource
      res <- server.use(_ => IO.never).as(ExitCode.Success)
    } yield res
}
