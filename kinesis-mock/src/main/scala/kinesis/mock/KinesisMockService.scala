/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock

import scala.concurrent.duration._

import cats.effect.Resource
import cats.effect.std.Semaphore
import cats.effect.{IO, ResourceApp}
import cats.implicits._
import com.comcast.ip4s.Host
import io.circe.syntax._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.SelfAwareStructuredLogger
import retry.RetryPolicies.constantDelay
import retry._

import kinesis.mock.api.{CreateStreamRequest, DescribeStreamSummaryRequest}
import kinesis.mock.cache.{Cache, CacheConfig}
import kinesis.mock.models.{AwsRegion, StreamName, StreamStatus}

object KinesisMockService extends ResourceApp.Forever {
  override def run(args: List[String]): Resource[IO, Unit] =
    for {
      logLevel <- ConsoleLogger.LogLevel.read.resource[IO]
      logger = new ConsoleLogger[IO](logLevel, this.getClass().getName())
      cacheConfig <- CacheConfig.read.resource[IO]
      context <- LoggingContext.create.toResource
      _ <- logger
        .info(
          context.addJson("cacheConfig", cacheConfig.asJson).context
        )(
          "Logging Cache Config"
        )
        .toResource
      cache <-
        if (cacheConfig.persistConfig.loadIfExists)
          Cache.loadFromFile(cacheConfig)
        else Cache(cacheConfig)
      _ <- initializeStreams(
        cache,
        cacheConfig.createStreamDuration,
        context,
        logger,
        cacheConfig.initializeStreams.getOrElse(Map.empty)
      ).toResource
      serviceConfig <- KinesisMockServiceConfig.read.resource[IO]
      tlsContext <- TLS.context(serviceConfig).toResource
      app = ErrorHandling.Recover.total(
        ErrorAction.log(
          Logger.httpApp(
            logHeaders = true,
            logBody = true,
            _ => false,
            logAction =
              Some((msg: String) => logger.trace(context.context)(msg))
          )(
            new KinesisMockRoutes(cache, logLevel).routes.orNotFound
          ),
          messageFailureLogAction =
            (t, msg) => logger.error(context.context, t)(msg),
          serviceErrorLogAction =
            (t, msg) => logger.error(context.context, t)(msg)
        )
      )
      host <- IO
        .fromOption(Host.fromString("0.0.0.0"))(
          new RuntimeException("Invalid hostname")
        )
        .toResource
      tlsServer = EmberServerBuilder
        .default[IO]
        .withPort(serviceConfig.tlsPort)
        .withHost(host)
        .withTLS(tlsContext)
        .withHttpApp(app)
        .withHttp2
        .build
      plainServer = EmberServerBuilder
        .default[IO]
        .withPort(serviceConfig.plainPort)
        .withHost(host)
        .withHttpApp(app)
        .withHttp2
        .build
      _ <- logger
        .info(
          s"Starting Kinesis TLS Mock Service on port ${serviceConfig.tlsPort}"
        )
        .toResource
      _ <- logger
        .info(
          s"Starting Kinesis Plain Mock Service on port ${serviceConfig.plainPort}"
        )
        .toResource
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
          LoggingContext.create.flatMap(context =>
            IO.pure(cacheConfig.persistConfig.shouldPersist)
              .ifM(cache.persistToDisk(context), IO.unit)
          )
        )
        .void
    } yield res

  def initializeStreams(
      cache: Cache,
      createStreamDuration: FiniteDuration,
      context: LoggingContext,
      logger: SelfAwareStructuredLogger[IO],
      streams: Map[AwsRegion, List[CreateStreamRequest]]
  ): IO[Unit] = {
    def isInitStreamDone(
        streamName: StreamName,
        region: AwsRegion
    ): IO[Boolean] = {
      val descReq = DescribeStreamSummaryRequest(Some(streamName), None)
      cache
        .describeStreamSummary(descReq, context, isCbor = false, Some(region))
        .map {
          case Left(_)  => false
          case Right(v) =>
            v.streamDescriptionSummary.streamStatus != StreamStatus.CREATING
        }
    }

    def initStream(req: CreateStreamRequest, region: AwsRegion): IO[Unit] =
      for {
        _ <- logger.info(
          s"Initializing stream '${req.streamName}' " +
            s"(shardCount=${req.shardCount})"
        )
        _ <- cache.createStream(req, context, isCbor = false, Some(region))
        _ <- retryingOnFailures[Boolean](
          RetryPolicies
            .limitRetries[IO](3)
            .join(constantDelay(createStreamDuration)),
          IO.pure,
          noop[IO, Boolean]
        )(isInitStreamDone(req.streamName, region))
      } yield {}

    streams.toList
      .parTraverse_ { case (region, s) =>
        for {
          semaphore <- Semaphore[IO](5)
          _ <- s.parTraverse { stream =>
            semaphore.permit.use(_ => initStream(stream, region).void)
          }
        } yield ()
      }
  }

  def persistDataLoop(
      shouldPersist: Boolean,
      interval: FiniteDuration,
      cache: Cache,
      logger: SelfAwareStructuredLogger[IO]
  ): IO[Unit] =
    LoggingContext.create.flatMap(context =>
      IO.pure(shouldPersist)
        .ifM(
          logger.info(context.context)("Starting persist data loop") >>
            retryingOnFailuresAndAllErrors[Unit](
              constantDelay[IO](interval),
              _ => IO.pure(false),
              noop[IO, Unit],
              (e: Throwable, _) =>
                logger.error(context.context, e)("Failed to persist data")
            )(cache.persistToDisk(context)),
          logger.info(context.context)(
            "Not configured to persist data, persist loop not started"
          )
        )
    )
}
