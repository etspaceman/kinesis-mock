package kinesis.mock

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.syntax._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry._

import kinesis.mock.api.{CreateStreamRequest, DescribeStreamSummaryRequest}
import kinesis.mock.cache.{Cache, CacheConfig}
import kinesis.mock.models.{StreamName, StreamStatus}

// $COVERAGE-OFF$
object KinesisMockService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use(blocker =>
      for {
        logger <- Slf4jLogger.create[IO]
        cacheConfig <- CacheConfig.read(blocker)
        context = LoggingContext.create
        _ <- logger.info(
          context.addJson("cacheConfig", cacheConfig.asJson).context
        )(
          "Logging Cache Config"
        )
        cache <- Cache(cacheConfig)
        _ <- initializeStreams(
          cache,
          cacheConfig.createStreamDuration,
          context,
          logger,
          cacheConfig.initializeStreams.toList.flatten
        )
        serviceConfig <- KinesisMockServiceConfig.read(blocker)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        context <- ssl.loadContextFromClasspath[IO](
          serviceConfig.keyStorePassword,
          serviceConfig.keyManagerPassword
        )
        http2Server = BlazeServerBuilder[IO](ExecutionContext.global)
          .bindHttp(serviceConfig.http2Port, "0.0.0.0")
          .withHttpApp(app)
          .withSslContext(context)
          .enableHttp2(
            true
          ) // This is bugged and HTTP2 unfortunately does not work correctly right now
          .resource
        http1PlainServer = BlazeServerBuilder[IO](ExecutionContext.global)
          .bindHttp(serviceConfig.http1PlainPort, "0.0.0.0")
          .withHttpApp(app)
          .resource
        _ <- logger.info(
          s"Starting Kinesis Http2 Mock Service on port ${serviceConfig.http2Port}"
        )
        _ <- logger.info(
          s"Starting Kinesis Http1 Plain Mock Service on port ${serviceConfig.http1PlainPort}"
        )
        res <- http2Server
          .parZip(http1PlainServer)
          .use(_ => IO.never)
          .as(ExitCode.Success)
      } yield res
    )

  def initializeStreams(
      cache: Cache,
      createStreamDuration: FiniteDuration,
      context: LoggingContext,
      logger: SelfAwareStructuredLogger[IO],
      streams: List[CreateStreamRequest]
  ): IO[Unit] = {
    def isCreateStreamDone(streamName: StreamName): IO[Boolean] = {
      val descReq = DescribeStreamSummaryRequest(streamName)
      cache
        .describeStreamSummary(descReq, context, isCbor = false)
        .map {
          case Left(_) => false
          case Right(v) =>
            v.streamDescriptionSummary.streamStatus != StreamStatus.CREATING
        }
    }

    def initStream(req: CreateStreamRequest): IO[Unit] =
      for {
        _ <- logger.info(
          s"Initializing stream '${req.streamName}' " +
            s"(shardCount=${req.shardCount})"
        )
        _ <- cache.createStream(req, context, isCbor = false)
        _ <- retryingOnFailures[Boolean](
          RetryPolicies
            .limitRetries[IO](3)
            .join(RetryPolicies.constantDelay(createStreamDuration)),
          identity,
          noop[IO, Boolean]
        )(isCreateStreamDone(req.streamName))
      } yield {}

    for {
      semaphore <- Semaphore[IO](5)
      _ <- streams
        .parTraverse(stream => semaphore.withPermit(initStream(stream).void))
    } yield {}
  }
}
// $COVERAGE-ON$
