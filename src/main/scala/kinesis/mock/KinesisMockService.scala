package kinesis.mock

import scala.concurrent.duration._

import cats.effect.std.Semaphore
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s.Host
import fs2.io.net.Network
import io.circe.syntax._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.syntax.kleisli._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.RetryPolicies.constantDelay
import retry._

import kinesis.mock.api.{CreateStreamRequest, DescribeStreamSummaryRequest}
import kinesis.mock.cache.{Cache, CacheConfig}
import kinesis.mock.models.{StreamName, StreamStatus}

// $COVERAGE-OFF$
object KinesisMockService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      logger <- Slf4jLogger.create[IO]
      cacheConfig <- CacheConfig.read
      context = LoggingContext.create
      _ <- logger.info(
        context.addJson("cacheConfig", cacheConfig.asJson).context
      )(
        "Logging Cache Config"
      )
      cache <- IO
        .pure(cacheConfig.persistConfig.loadIfExists)
        .ifM(
          Cache.loadFromFile(cacheConfig),
          Cache(cacheConfig)
        )
      _ <- initializeStreams(
        cache,
        cacheConfig.createStreamDuration,
        context,
        logger,
        cacheConfig.initializeStreams.toVector.flatten
      )
      serviceConfig <- KinesisMockServiceConfig.read
      app = Logger.httpApp(true, true, _ => false)(
        new KinesisMockRoutes(cache).routes.orNotFound
      )
      tlsContext <- Network[IO].tlsContext.fromKeyStoreResource(
        "server.jks",
        serviceConfig.keyStorePassword.toCharArray(),
        serviceConfig.keyManagerPassword.toCharArray()
      )
      host <- IO.fromOption(Host.fromString("0.0.0.0"))(
        new RuntimeException("Invalid hostname")
      )
      tlsServer = EmberServerBuilder
        .default[IO]
        .withPort(serviceConfig.tlsPort)
        .withHost(host)
        .withTLS(tlsContext)
        .withHttpApp(app)
        .build
      plainServer = EmberServerBuilder
        .default[IO]
        .withPort(serviceConfig.plainPort)
        .withHost(host)
        .withHttpApp(app)
        .build
      _ <- logger.info(
        s"Starting Kinesis TLS Mock Service on port ${serviceConfig.tlsPort}"
      )
      _ <- logger.info(
        s"Starting Kinesis Plain Mock Service on port ${serviceConfig.plainPort}"
      )
      res <- tlsServer
        .both(plainServer)
        .both(
          persistDataLoop(
            cacheConfig.persistConfig.shouldPersist,
            cacheConfig.persistConfig.interval,
            cache,
            logger
          ).background
        )
        .onFinalize(
          IO.pure(cacheConfig.persistConfig.shouldPersist)
            .ifM(cache.persistToDisk(LoggingContext.create), IO.unit)
        )
        .use(_ => IO.never)
        .as(ExitCode.Success)
    } yield res

  def initializeStreams(
      cache: Cache,
      createStreamDuration: FiniteDuration,
      context: LoggingContext,
      logger: SelfAwareStructuredLogger[IO],
      streams: Vector[CreateStreamRequest]
  ): IO[Unit] = {
    def isInitStreamDone(streamName: StreamName): IO[Boolean] = {
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
            .join(constantDelay(createStreamDuration)),
          IO.pure,
          noop[IO, Boolean]
        )(isInitStreamDone(req.streamName))
      } yield {}

    for {
      semaphore <- Semaphore[IO](5)
      _ <- streams
        .parTraverse(stream =>
          semaphore.permit.use(_ => initStream(stream).void)
        )
    } yield {}
  }

  def persistDataLoop(
      shouldPersist: Boolean,
      interval: FiniteDuration,
      cache: Cache,
      logger: SelfAwareStructuredLogger[IO],
      blocker: Blocker
  ): IO[Unit] = {
    val context = LoggingContext.create
    IO.pure(shouldPersist)
      .ifM(
        logger.info(context.context)("Starting persist data loop") >>
          retryingOnFailuresAndAllErrors[Unit](
            constantDelay[IO](interval),
            _ => IO.pure(false),
            noop[IO, Unit],
            (e: Throwable, _) =>
              logger.error(context.context, e)("Failed to persist data")
          )(cache.persistToDisk(context, blocker)),
        logger.info(LoggingContext.create.context)(
          "Not configured to persist data, persist loop not started"
        )
      )
  }
}
// $COVERAGE-ON$
