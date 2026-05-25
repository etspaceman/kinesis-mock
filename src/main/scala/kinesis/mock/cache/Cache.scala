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
package cache

import cats.effect.*
import cats.effect.std.{Semaphore, Supervisor}
import cats.syntax.all.*
import fs2.io.file.*
import fs2.{Chunk, Stream}
import io.circe.Printer
import io.circe.fs2.*
import io.circe.syntax.*
import org.typelevel.log4cats.SelfAwareStructuredLogger

import kinesis.mock.api.*
import kinesis.mock.models.*
import kinesis.mock.syntax.semaphore.*

class Cache private (
    streamsRef: Ref[IO, Streams],
    semaphores: Ref[IO, Map[AwsRegion, CacheSemaphores]],
    persistDataSemaphore: Semaphore[IO],
    config: CacheConfig,
    supervisor: Supervisor[IO]
):
  self =>

  val logger: SelfAwareStructuredLogger[IO] =
    new ConsoleLogger(config.logLevel, self.getClass().getName())

  private def getStreamArn(
      streamArn: Option[StreamArn],
      streamName: Option[StreamName],
      region: Option[AwsRegion]
  ): IO[StreamArn] = IO.fromOption(
    streamArn.orElse(
      streamName.map(name =>
        StreamArn(
          region.getOrElse(config.awsRegion),
          name,
          config.awsAccountId
        )
      )
    )
  )(new RuntimeException("Unexpected outcome recreating StreamArn"))

  private def getSemaphores(region: Option[AwsRegion]): IO[CacheSemaphores] =
    region match
      case None    => semaphores.get.map(_(config.awsRegion))
      case Some(r) =>
        for
          current <- semaphores.get
          res <- current.get(r) match
            case Some(found) => IO.pure(found)
            case None        =>
              for
                created <- CacheSemaphores.create
                _ <- semaphores.update(x => x + (r -> created))
              yield created
        yield res

  def addTagsToStream(
      req: AddTagsToStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)("Processing AddTagsToStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.addTagsToStream.tryAcquireRelease(
          req
            .addTagsToStream(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatTap(
              _.fold(
                e =>
                  logger.warn(ctx.context, e)(
                    "Adding tags to stream was unuccessful"
                  ),
                _ =>
                  logger.debug(ctx.context)(
                    "Successfully added tags to the stream"
                  )
              )
            ),
          logger
            .warn(ctx.context)("Rate limit exceeded for AddTagsToStream")
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for AddTagsToStream"
                )
              )
            )
        )
      )
  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)("Processing RemoveTagsFromStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.removeTagsFromStream.tryAcquireRelease(
          req
            .removeTagsFromStream(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatTap(
              _.fold(
                e =>
                  logger.warn(ctx.context, e)(
                    "Removing tags from stream was unuccessful"
                  ),
                _ =>
                  logger.debug(ctx.context)(
                    "Successfully removed tags from the stream"
                  )
              )
            ),
          logger
            .warn(ctx.context)("Rate limit exceeded for RemoveTagsFromStream")
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for RemoveTagsFromStream"
                )
              )
            )
        )
      )

  def createStream(
      req: CreateStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing CreateStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.createStream.tryAcquireRelease(
          for
            createStreamsRes <- req
              .createStream(
                streamsRef,
                config.shardLimit,
                config.onDemandStreamCountLimit,
                region.getOrElse(config.awsRegion),
                config.awsAccountId
              )
            _ <- createStreamsRes.fold(
              e =>
                logger.warn(ctx.context, e)(
                  "Creating stream was unuccessful"
                ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully created stream"
                )
            )
            _ <- supervisor
              .supervise(
                logger.debug(ctx.context)(
                  s"Delaying setting stream to active for ${config.createStreamDuration.toString}"
                ) *>
                  IO.sleep(config.createStreamDuration) *>
                  logger.debug(ctx.context)(
                    s"Setting stream to active"
                  ) *>
                  streamsRef
                    .update(streams =>
                      streams.findAndUpdateStream(
                        StreamArn(
                          region.getOrElse(config.awsRegion),
                          req.streamName,
                          config.awsAccountId
                        )
                      )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                    )
              )
              .void
          yield createStreamsRes,
          logger
            .warn(ctx.context)("Rate limit exceeded for CreateStream")
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for CreateStream"
                )
              )
            )
        )
      )

  def deleteStream(
      req: DeleteStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)("Processing DeleteStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.deleteStream.tryAcquireRelease(
          for
            deleteStreamRes <- req.deleteStream(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            _ <- deleteStreamRes.fold(
              e =>
                logger.warn(ctx.context, e)(
                  "Deleting stream was unuccessful"
                ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully deleted stream"
                )
            )
            _ <- supervisor
              .supervise(
                for
                  _ <- logger.debug(ctx.context)(
                    s"Delaying removing the stream for ${config.deleteStreamDuration.toString}"
                  )
                  _ <- IO.sleep(config.deleteStreamDuration)
                  _ <- logger.debug(ctx.context)(
                    s"Removing stream"
                  )
                  streamArn <- getStreamArn(
                    req.streamArn,
                    req.streamName,
                    region
                  )
                  _ <- streamsRef.update(x => x.removeStream(streamArn))
                yield ()
              )
              .void
          yield deleteStreamRes,
          logger
            .warn(ctx.context)("Rate limit exceeded for DeleteStream")
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for DeleteStream"
                )
              )
            )
        )
      )

  def decreaseStreamRetention(
      req: DecreaseStreamRetentionPeriodRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing DecreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .decreaseStreamRetention(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatTap(
          _.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Decreasing the stream retention period was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully decreased the stream retention period "
              )
          )
        )

  def increaseStreamRetention(
      req: IncreaseStreamRetentionPeriodRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing IncreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .increaseStreamRetention(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatTap(
          _.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Increasing the stream retention period was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully increased the stream retention period "
              )
          )
        )

  def describeLimits(
      context: LoggingContext,
      region: Option[AwsRegion]
  ): IO[Response[DescribeLimitsResponse]] =
    logger.debug(context.context)("Processing DescribeLimits request") *>
      getSemaphores(region).flatMap(
        _.describeLimits.tryAcquireRelease(
          DescribeLimitsResponse
            .get(
              config.shardLimit,
              config.onDemandStreamCountLimit,
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap(response =>
              logger
                .debug(context.context)("Successfully described limits") *>
                logger
                  .trace(
                    context.addJson("response", response.asJson).context
                  )(
                    "Logging response"
                  )
                  .as(Right(response))
            ),
          logger
            .warn(context.context)("Rate limit exceeded for DescribeLimits")
            .as(
              Left(
                LimitExceededException("Rate limit exceeded for DescribeLimits")
              )
            )
        )
      )

  def describeStream(
      req: DescribeStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[DescribeStreamResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing DescribeStream request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.describeStream.tryAcquireRelease(
          req
            .describeStream(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap { response =>
              response.fold(
                e =>
                  logger
                    .warn(ctx.context, e)(
                      "Describing the stream was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(ctx.context)(
                    "Successfully described the stream"
                  ) *> logger
                    .trace(ctx.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            },
          logger
            .warn(context.context)("Rate limit exceeded for DescribeStream")
            .as(
              Left(
                LimitExceededException("Rate limit exceeded for DescribeStream")
              )
            )
        )
      )

  def describeStreamSummary(
      req: DescribeStreamSummaryRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[DescribeStreamSummaryResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing DescribeStreamSummary request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.describeStreamSummary.tryAcquireRelease(
          req
            .describeStreamSummary(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(ctx.context, e)(
                      "Describing the stream summary was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(ctx.context)(
                    "Successfully described the stream summary"
                  ) *> logger
                    .trace(ctx.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for DescribeStreamSummary"
            )
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for DescribeStreamSummary"
                )
              )
            )
        )
      )

  def registerStreamConsumer(
      req: RegisterStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[RegisterStreamConsumerResponse]] =
    val ctx = context + ("streamArn" -> req.streamArn.streamArn)
    logger.debug(ctx.context)(
      "Processing RegisterStreamConsumer request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(req.streamArn.awsRegion.some).flatMap(
        _.registerStreamConsumer.tryAcquireRelease(
          req
            .registerStreamConsumer(streamsRef)
            .flatMap(response =>
              response
                .fold(
                  e =>
                    logger
                      .warn(ctx.context, e)(
                        "Describing the stream summary was unuccessful"
                      )
                      .as(response),
                  r =>
                    logger.debug(ctx.context)(
                      "Successfully described the stream summary"
                    ) *> logger
                      .trace(ctx.addEncoded("response", r, isCbor).context)(
                        "Logging response"
                      ) *> supervisor
                      .supervise(
                        logger.debug(ctx.context)(
                          s"Delaying setting the consumer as ACTIVE for ${config.registerStreamConsumerDuration.toString}"
                        ) *>
                          IO.sleep(config.registerStreamConsumerDuration) *>
                          logger.debug(ctx.context)(
                            s"Setting consumer as ACTIVE"
                          ) *> streamsRef.update(x =>
                            x.streams.values
                              .find(_.streamArn == req.streamArn)
                              .fold(x)(stream =>
                                x.updateStream(
                                  stream.copy(consumers =
                                    stream.consumers ++ Vector(
                                      r.consumer.consumerName -> Consumer(
                                        r.consumer.consumerArn,
                                        r.consumer.consumerCreationTimestamp,
                                        r.consumer.consumerName,
                                        ConsumerStatus.ACTIVE,
                                        req.streamArn
                                      )
                                    )
                                  )
                                )
                              )
                          )
                      )
                      .void
                      .as(response)
                )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for RegisterStreamConsumer"
            )
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for RegisterStreamConsumer"
                )
              )
            )
        )
      )

  def deregisterStreamConsumer(
      req: DeregisterStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] =
    logger.debug(context.context)(
      "Processing DeregisterStreamConsumer request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(
        req.consumerArn
          .map(_.streamArn.awsRegion)
          .orElse(req.streamArn.map(_.awsRegion))
      ).flatMap(
        _.deregisterStreamConsumer.tryAcquireRelease(
          req
            .deregisterStreamConsumer(streamsRef)
            .flatMap(response =>
              response
                .fold(
                  e =>
                    logger
                      .warn(context.context, e)(
                        "Deregistering the stream consumer was unuccessful"
                      )
                      .as(response.as(())),
                  consumer =>
                    logger.debug(context.context)(
                      "Successfully registered the stream consumer"
                    ) *> supervisor
                      .supervise(
                        logger.debug(context.context)(
                          s"Delaying removing the consumer for ${config.deregisterStreamConsumerDuration.toString}"
                        ) *>
                          IO.sleep(config.deregisterStreamConsumerDuration) *>
                          logger.debug(context.context)(
                            s"Removing the consumer"
                          ) *>
                          streamsRef.update(x =>
                            x.streams.values
                              .find(s =>
                                s.consumers.keys.toVector
                                  .contains(consumer.consumerName)
                              )
                              .fold(x)(stream =>
                                x.updateStream(
                                  stream
                                    .copy(consumers =
                                      stream.consumers.filterNot {
                                        case (consumerName, _) =>
                                          consumerName == consumer.consumerName
                                      }
                                    )
                                )
                              )
                          )
                      )
                      .void
                      .as(response.as(()))
                )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for DeregisterStreamConsumer"
            )
            .as(
              Left(
                LimitExceededException(
                  "Rate limit exceeded for DeregisterStreamConsumer"
                )
              )
            )
        )
      )

  def describeStreamConsumer(
      req: DescribeStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[DescribeStreamConsumerResponse]] =
    logger.debug(context.context)(
      "Processing DescribeStreamConsumer request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(
        req.consumerArn
          .map(_.streamArn.awsRegion)
          .orElse(req.streamArn.map(_.awsRegion))
      ).flatMap(
        _.describeStreamConsumer.tryAcquireRelease(
          req
            .describeStreamConsumer(streamsRef)
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Describing the stream consumer was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(context.context)(
                    "Successfully described the stream consumer"
                  ) *> logger
                    .trace(context.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for DescribeStreamConsumer"
            )
            .as(
              Left(
                LimitExceededException(
                  "Limit exceeded for DescribeStreamConsumer"
                )
              )
            )
        )
      )

  def disableEnhancedMonitoring(
      req: DisableEnhancedMonitoringRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[DisableEnhancedMonitoringResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing DisableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .disableEnhancedMonitoring(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response.fold(
            e =>
              logger
                .warn(context.context, e)(
                  "Disabling the enhanced monitoring was unuccessful"
                )
                .as(response),
            r =>
              logger.debug(context.context)(
                "Successfully disabled enhanced monitoring"
              ) *> logger
                .trace(context.addEncoded("response", r, isCbor).context)(
                  "Logging response"
                )
                .as(response)
          )
        )

  def enableEnhancedMonitoring(
      req: EnableEnhancedMonitoringRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[EnableEnhancedMonitoringResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing EnableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .enableEnhancedMonitoring(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response.fold(
            e =>
              logger
                .warn(context.context, e)(
                  "Enabling the enhanced monitoring was unuccessful"
                )
                .as(response),
            r =>
              logger.debug(context.context)(
                "Successfully enabled enhanced monitoring"
              ) *> logger
                .trace(context.addEncoded("response", r, isCbor).context)(
                  "Logging response"
                )
                .as(response)
          )
        )

  def listShards(
      req: ListShardsRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[ListShardsResponse]] =
    logger.debug(context.context)(
      "Processing ListShards request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.listShards.tryAcquireRelease(
          req
            .listShards(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Listing shards was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(context.context)(
                    "Successfully listed shards"
                  ) *> logger
                    .trace(context.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for ListShards"
            )
            .as(Left(LimitExceededException("Limit exceeded for ListShards")))
        )
      )

  def listStreamConsumers(
      req: ListStreamConsumersRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[ListStreamConsumersResponse]] =
    val ctx = context + ("streamArn" -> req.streamArn.streamArn)
    logger.debug(ctx.context)(
      "Processing ListStreamConsumers request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(req.streamArn.awsRegion.some).flatMap(
        _.listStreamConsumers.tryAcquireRelease(
          req
            .listStreamConsumers(streamsRef)
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Listing stream consumers was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(context.context)(
                    "Successfully listed stream consumers"
                  ) *> logger
                    .trace(context.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(ctx.context)(
              "Rate limit exceeded for ListShards"
            )
            .as(
              Left(
                LimitExceededException("Limit exceeded for ListStreamConsumers")
              )
            )
        )
      )

  def listStreams(
      req: ListStreamsRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[ListStreamsResponse]] =
    logger.debug(context.context)(
      "Processing ListStreams request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.listStreams.tryAcquireRelease(
          req
            .listStreams(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Listing streams was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(context.context)(
                    "Successfully listed streams"
                  ) *> logger
                    .trace(context.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(context.context)(
              "Rate limit exceeded for ListStreams"
            )
            .as(
              Left(
                LimitExceededException("Limit exceeded for ListStreams")
              )
            )
        )
      )

  def listTagsForStream(
      req: ListTagsForStreamRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[ListTagsForStreamResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing ListTagsForStream request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.listTagsForStream.tryAcquireRelease(
          req
            .listTagsForStream(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            .flatMap(response =>
              response.fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Listing tags for stream was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(context.context)(
                    "Successfully listed tags for stream"
                  ) *> logger
                    .trace(context.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    )
                    .as(response)
              )
            ),
          logger
            .warn(ctx.context)(
              "Rate limit exceeded for ListTagsForStream"
            )
            .as(
              Left(
                LimitExceededException("Limit exceeded for ListTagsForStream")
              )
            )
        )
      )

  def startStreamEncryption(
      req: StartStreamEncryptionRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing StartStreamEncryption request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .startStreamEncryption(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Starting stream encryption was unuccessful"
                  )
                  .as(response),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully started stream encryption"
                ) *> supervisor
                  .supervise(
                    for
                      _ <- logger.debug(context.context)(
                        s"Delaying setting the stream to active for ${config.startStreamEncryptionDuration.toString}"
                      )
                      _ <- IO.sleep(config.startStreamEncryptionDuration)
                      _ <- logger.debug(context.context)(
                        s"Setting the stream to active"
                      )
                      streamArn <- getStreamArn(
                        req.streamArn,
                        req.streamName,
                        region
                      )
                      _ <- streamsRef
                        .update(updated =>
                          updated.findAndUpdateStream(
                            streamArn
                          )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                        )
                    yield ()
                  )
                  .as(response)
            )
        )

  def stopStreamEncryption(
      req: StopStreamEncryptionRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing StopStreamEncryption request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .stopStreamEncryption(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Stopping stream encryption was unuccessful"
                  )
                  .as(response),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully stopped stream encryption"
                ) *>
                  supervisor
                    .supervise(
                      for
                        _ <- logger.debug(context.context)(
                          s"Delaying setting the stream to active for ${config.stopStreamEncryptionDuration.toString}"
                        )
                        _ <- IO.sleep(config.stopStreamEncryptionDuration)
                        _ <- logger.debug(context.context)(
                          s"Setting the stream to active"
                        )
                        streamArn <- getStreamArn(
                          req.streamArn,
                          req.streamName,
                          region
                        )
                        _ <- streamsRef
                          .update(updated =>
                            updated.findAndUpdateStream(
                              streamArn
                            )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                          )
                      yield ()
                    )
                    .void
                    .as(response)
            )
        )

  def getShardIterator(
      req: GetShardIteratorRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[GetShardIteratorResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing GetShardIterator request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .getShardIterator(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Getting the shard iterator was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(ctx.context)(
                  "Successfully got the shard iterator"
                ) *> logger
                  .trace(ctx.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
        )

  def getRecords(
      req: GetRecordsRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[GetRecordsResponse]] =
    logger.debug(context.context)(
      "Processing GetRecords request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .getRecords(
          streamsRef,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Getting records was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully got records"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
        )

  def putRecord(
      req: PutRecordRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[PutRecordResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    for
      _ <- logger.debug(ctx.context)("Processing PutRecord request")
      _ <- logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      )
      res <- req.putRecord(
        streamsRef,
        region.getOrElse(config.awsRegion),
        config.awsAccountId
      )
      _ <- res.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting record was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put record"
          ) *> logger
            .trace(ctx.addEncoded("response", r, isCbor).context)(
              "Logging response"
            )
      )
    yield res

  def putRecords(
      req: PutRecordsRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[PutRecordsResponse]] =
    val ctx = context ++
      req.streamName.map(x => "streamName" -> x.streamName).toList ++
      req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    for
      _ <- logger.debug(ctx.context)("Processing PutRecords request")
      _ <- logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      )
      res <- req.putRecords(
        streamsRef,
        region.getOrElse(config.awsRegion),
        config.awsAccountId
      )
      _ <- res.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting records was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put records"
          ) *> logger
            .trace(ctx.addEncoded("response", r, isCbor).context)(
              "Logging response"
            )
      )
    yield res

  def mergeShards(
      req: MergeShardsRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing MergeShards request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.mergeShards.tryAcquireRelease(
          for
            result <- req.mergeShards(
              streamsRef,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            _ <- result.fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Merging shards was unuccessful"
                  ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully merged shards"
                )
            )
            _ <- supervisor
              .supervise(
                for
                  _ <- logger.debug(context.context)(
                    s"Delaying setting the stream to active for ${config.mergeShardsDuration.toString}"
                  )
                  _ <- IO.sleep(config.mergeShardsDuration)
                  _ <- logger.debug(context.context)(
                    s"Setting the stream to active"
                  )
                  streamArn <- getStreamArn(
                    req.streamArn,
                    req.streamName,
                    region
                  )
                  _ <- streamsRef
                    .update(updated =>
                      updated.findAndUpdateStream(
                        streamArn
                      )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                    )
                yield ()
              )
              .void
          yield result,
          logger
            .warn(ctx.context)(
              "Rate limit exceeded for MergeShards"
            )
            .as(Left(LimitExceededException("Limit Exceeded for MergeShards")))
        )
      )

  def splitShard(
      req: SplitShardRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[Unit]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing SplitShard request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      getSemaphores(region).flatMap(
        _.splitShard.tryAcquireRelease(
          for
            result <- req.splitShard(
              streamsRef,
              config.shardLimit,
              region.getOrElse(config.awsRegion),
              config.awsAccountId
            )
            _ <- result.fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Splitting shard was unuccessful"
                  ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully split shard"
                )
            )
            _ <- supervisor
              .supervise(
                for
                  _ <- logger.debug(context.context)(
                    s"Delaying setting the stream to active for ${config.splitShardDuration.toString}"
                  )
                  _ <- IO.sleep(config.splitShardDuration)
                  _ <- logger.debug(context.context)(
                    s"Setting the stream to active"
                  )
                  streamArn <- getStreamArn(
                    req.streamArn,
                    req.streamName,
                    region
                  )
                  _ <- streamsRef
                    .update(updated =>
                      updated.findAndUpdateStream(
                        streamArn
                      )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                    )
                yield ()
              )
              .void
          yield result,
          logger
            .warn(ctx.context)(
              "Rate limit exceeded for MergeShards"
            )
            .as(Left(LimitExceededException("Limit Exceeded for SplitShard")))
        )
      )

  def updateShardCount(
      req: UpdateShardCountRequest,
      context: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  ): IO[Response[UpdateShardCountResponse]] =
    val ctx =
      context ++
        req.streamName.map(x => "streamName" -> x.streamName).toList ++
        req.streamArn.map(x => "streamArn" -> x.streamArn).toList
    logger.debug(ctx.context)(
      "Processing UpdateShardCount request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *> (for
        result <- req.updateShardCount(
          streamsRef,
          config.shardLimit,
          region.getOrElse(config.awsRegion),
          config.awsAccountId
        )
        _ <- result.fold(
          e =>
            logger
              .warn(ctx.context, e)(
                "Updating shard count was unuccessful"
              ),
          _ =>
            logger.debug(ctx.context)(
              "Successfully updated shard count"
            )
        )
        _ <- supervisor
          .supervise(
            for
              _ <- logger.debug(context.context)(
                s"Delaying setting the stream to active for ${config.updateShardCountDuration.toString}"
              )
              _ <- IO.sleep(config.updateShardCountDuration)
              _ <- logger.debug(context.context)(
                s"Setting the stream to active"
              )
              streamArn <- getStreamArn(req.streamArn, req.streamName, region)
              _ <- streamsRef
                .update(updated =>
                  updated.findAndUpdateStream(
                    streamArn
                  )(x => x.copy(streamStatus = StreamStatus.ACTIVE))
                )
            yield ()
          )
          .void
      yield result)

  def updateStreamMode(
      req: UpdateStreamModeRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] =
    val ctx = context + ("streamArn" -> req.streamArn.streamArn)
    logger.debug(ctx.context)(
      "Processing UpdateStreamMode request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *> (for
        result <- req.updateStreamMode(
          streamsRef,
          config.onDemandStreamCountLimit
        )
        _ <- result.fold(
          e =>
            logger
              .warn(ctx.context, e)(
                "Updating stream mode was unuccessful"
              ),
          _ =>
            logger.debug(ctx.context)(
              "Successfully updated stream mode"
            )
        )
        _ <- supervisor
          .supervise(
            logger.debug(context.context)(
              s"Delaying setting the stream to active for ${config.updateStreamModeDuration.toString}"
            ) *>
              IO.sleep(config.updateStreamModeDuration) *>
              logger.debug(context.context)(
                s"Setting the stream to active"
              ) *>
              streamsRef
                .update(updated =>
                  updated.findAndUpdateStream(req.streamArn)(x =>
                    x.copy(streamStatus = StreamStatus.ACTIVE)
                  )
                )
          )
          .void
      yield result)

  def persistToDisk(context: LoggingContext): IO[Unit] =
    IO.pure(config.persistConfig.shouldPersist)
      .ifM(
        persistDataSemaphore.permit.use(_ =>
          for
            streams <- streamsRef.get
            ctx = context ++ Vector(
              "fileName" -> config.persistConfig.fileName,
              "path" -> config.persistConfig.osPath.toString
            )
            _ <- Files[IO]
              .exists(config.persistConfig.osPath)
              .ifM(
                IO.unit,
                logger.info(ctx.context)("Creating directories") >>
                  Files[IO].createDirectories(config.persistConfig.osPath)
              )
            _ <- logger
              .debug(ctx.context)("Persisting stream data to disk")
            res <- Stream
              .chunk(
                Chunk.ByteBuffer
                  .view(Printer.noSpaces.printToByteBuffer(streams.asJson))
              )
              .through(
                Files[IO].writeAll(config.persistConfig.osFile)
              )
              .compile
              .drain
            _ <- logger
              .debug(ctx.context)("Successfully persisted stream data")
          yield res
        ),
        logger
          .warn(context.context)("Persist config was not provided, ignoring")
      )

object Cache:
  def apply(
      config: CacheConfig,
      streams: Streams = Streams.empty // scalafix:ok
  )(using C: Concurrent[IO]): Resource[IO, Cache] = for
    ref <- Ref.of[IO, Streams](streams).toResource
    semaphores <- CacheSemaphores.create.toResource
    semaphoresRef <- Ref
      .of[IO, Map[AwsRegion, CacheSemaphores]](
        Map(config.awsRegion -> semaphores)
      )
      .toResource
    persistDataSemaphore <- Semaphore[IO](1).toResource
    supervisor <- Supervisor[IO]
  yield new Cache(
    ref,
    semaphoresRef,
    persistDataSemaphore,
    config,
    supervisor
  )

  def loadFromFile(
      config: CacheConfig
  )(using C: Concurrent[IO]): Resource[IO, Cache] =
    for
      exists <- Files[IO].exists(config.persistConfig.osFile).toResource
      res <-
        if exists then
          for
            streams <- Files[IO]
              .readAll(config.persistConfig.osFile)
              .through(byteArrayParser)
              .through(decoder[IO, Streams])
              .compile
              .lastOrError
              .toResource
            cache <- apply(config, streams)
          yield cache
        else apply(config)
    yield res
