package kinesis.mock
package cache

import cats.Parallel
import cats.effect._
import cats.effect.concurrent.Supervisor
import cats.syntax.all._
import io.circe.syntax._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import kinesis.mock.api._
import kinesis.mock.models._
import kinesis.mock.syntax.semaphore._
import cats.effect.{ Ref, Temporal }
import cats.effect.implicits._
import cats.effect.std.Semaphore

class Cache private (
    streamsRef: Ref[IO, Streams],
    shardsSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]],
    semaphores: CacheSemaphores,
    config: CacheConfig,
    supervisor: Supervisor[IO]
) {

  val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def addTagsToStream(
      req: AddTagsToStreamRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing AddTagsToStream request") *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.addTagsToStream.tryAcquireRelease(
        streamsRef.get.flatMap(streams =>
          req
            .addTagsToStream(streams)
            .traverse(streamsRef.set)
            .map(_.toEither.leftMap(KinesisMockException.aggregate))
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
  }
  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing RemoveTagsFromStream request") *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.removeTagsFromStream.tryAcquireRelease(
        streamsRef.get.flatMap(streams =>
          req
            .removeTagsFromStream(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)
            .traverse(streamsRef.set)
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
  }

  def createStream(
      req: CreateStreamRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing CreateStream request") *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.createStream.tryAcquireRelease(
        for {
          streams <- streamsRef.get
          createStreamsRes = req
            .createStream(
              streams,
              config.shardLimit,
              config.awsRegion,
              config.awsAccountId
            )
            .toEither
            .leftMap(KinesisMockException.aggregate)
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
          res <- createStreamsRes
            .traverse { case (updated, shardSemaphoreKeys) =>
              for {
                _ <- streamsRef.set(updated)
                newShardSemaphores <- shardSemaphoreKeys
                  .traverse(key => Semaphore[IO](1).map(s => key -> s))
                _ <- shardsSemaphoresRef.update(shardSemaphores =>
                  shardSemaphores ++ newShardSemaphores
                )
                r <- supervisor
                  .supervise(
                    logger.debug(ctx.context)(
                      s"Delaying setting stream to active for ${config.createStreamDuration.toString}"
                    ) *>
                      IO.sleep(config.createStreamDuration) *>
                      logger.debug(ctx.context)(
                        s"Setting stream to active"
                      ) *>
                      streamsRef
                        .set(
                          updated.findAndUpdateStream(req.streamName)(x =>
                            x.copy(streamStatus = StreamStatus.ACTIVE)
                          )
                        )
                  )
                  .void
              } yield r
            }
        } yield res,
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
  }

  def deleteStream(
      req: DeleteStreamRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing DeleteStream request") *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.deleteStream.tryAcquireRelease(
        for {
          streams <- streamsRef.get
          deleteStreamrRes = req
            .deleteStream(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)
          _ <- deleteStreamrRes.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Deleting stream was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully deleted stream"
              )
          )
          res <-
            deleteStreamrRes.traverse { case (updated, shardSemaphoreKeys) =>
              for {
                _ <- streamsRef.set(updated)
                _ <- shardsSemaphoresRef.update(shardsSemaphores =>
                  shardsSemaphores -- shardSemaphoreKeys
                )
                r <- supervisor
                  .supervise(
                    logger.debug(ctx.context)(
                      s"Delaying removing the stream for ${config.deleteStreamDuration.toString}"
                    ) *>
                      IO.sleep(config.deleteStreamDuration) *>
                      logger.debug(ctx.context)(
                        s"Removing stream"
                      ) *>
                      streamsRef.set(
                        updated.removeStream(req.streamName)
                      )
                  )
                  .void
              } yield r

            }
        } yield res,
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
  }

  def decreaseStreamRetention(
      req: DecreaseStreamRetentionPeriodRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DecreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap(streams =>
        req
          .decreaseStreamRetention(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(streamsRef.set)
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
      )
  }

  def increaseStreamRetention(
      req: IncreaseStreamRetentionPeriodRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing IncreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap(streams =>
        req
          .increaseStreamRetention(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(streamsRef.set)
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
      )
  }

  def describeLimits(
      context: LoggingContext
  ): IO[Either[KinesisMockException, DescribeLimitsResponse]] =
    logger.debug(context.context)("Processing DescribeLimits request") *>
      semaphores.describeLimits.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = DescribeLimitsResponse.get(config.shardLimit, streams)
          logger.debug(context.context)("Successfully described limits") *>
            logger
              .trace(context.addJson("response", response.asJson).context)(
                "Logging response"
              )
              .as(Right(response))
        },
        logger
          .warn(context.context)("Rate limit exceeded for DescribeLimits")
          .as(
            Left(
              LimitExceededException("Rate limit exceeded for DescribeLimits")
            )
          )
      )

  def describeStream(
      req: DescribeStreamRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, DescribeStreamResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DescribeStream request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.describeStream.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .describeStream(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(ctx.addJson("response", r.asJson).context)(
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
  }

  def describeStreamSummary(
      req: DescribeStreamSummaryRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, DescribeStreamSummaryResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DescribeStreamSummary request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.describeStreamSummary.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .describeStreamSummary(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(ctx.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
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
  }

  def registerStreamConsumer(
      req: RegisterStreamConsumerRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, RegisterStreamConsumerResponse]] = {
    val ctx = context + ("streamArn" -> req.streamArn)
    logger.debug(ctx.context)(
      "Processing RegisterStreamConsumer request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.registerStreamConsumer.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .registerStreamConsumer(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Describing the stream summary was unuccessful"
                  ),
              r =>
                logger.debug(ctx.context)(
                  "Successfully described the stream summary"
                ) *> logger
                  .trace(ctx.addJson("response", r._2.asJson).context)(
                    "Logging response"
                  )
            ) *> response
            .traverse { case (updated, response) =>
              streamsRef
                .set(updated)
                .flatMap { _ =>
                  supervisor
                    .supervise(
                      logger.debug(ctx.context)(
                        s"Delaying setting the consumer as ACTIVE for ${config.registerStreamConsumerDuration.toString}"
                      ) *>
                        IO.sleep(config.registerStreamConsumerDuration) *>
                        logger.debug(ctx.context)(
                          s"Setting consumer as ACTIVE"
                        ) *>
                        updated.streams.values
                          .find(_.streamArn == req.streamArn)
                          .traverse(stream =>
                            streamsRef.set(
                              updated.updateStream(
                                stream.copy(consumers =
                                  stream.consumers ++ List(
                                    response.consumer.consumerName -> response.consumer
                                      .copy(consumerStatus =
                                        ConsumerStatus.ACTIVE
                                      )
                                  )
                                )
                              )
                            )
                          )
                    )
                    .void
                }
                .as(response)
            }
        },
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
  }

  def deregisterStreamConsumer(
      req: DeregisterStreamConsumerRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    logger.debug(context.context)(
      "Processing DeregisterStreamConsumer request"
    ) *>
      logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.deregisterStreamConsumer.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .deregisterStreamConsumer(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

          response
            .fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Deregistering the stream consumer was unuccessful"
                  ),
              _ =>
                logger.debug(context.context)(
                  "Successfully registered the stream consumer"
                )
            ) *> response.traverse { case (updated, consumer) =>
            streamsRef
              .set(updated)
              .flatMap { _ =>
                supervisor
                  .supervise(
                    logger.debug(context.context)(
                      s"Delaying removing the consumer for ${config.deregisterStreamConsumerDuration.toString}"
                    ) *>
                      IO.sleep(config.deregisterStreamConsumerDuration) *>
                      logger.debug(context.context)(
                        s"Removing the consumer"
                      ) *>
                      updated.streams.values
                        .find(s =>
                          s.consumers.keys.toList
                            .contains(consumer.consumerName)
                        )
                        .traverse(stream =>
                          streamsRef.set(
                            updated.updateStream(
                              stream.copy(consumers =
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
              }
          }
        },
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
  }

  def describeStreamConsumer(
      req: DescribeStreamConsumerRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, DescribeStreamConsumerResponse]] =
    logger.debug(context.context)(
      "Processing DescribeStreamConsumer request"
    ) *>
      logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.describeStreamConsumer.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .describeStreamConsumer(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
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

  def disableEnhancedMonitoring(
      req: DisableEnhancedMonitoringRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, DisableEnhancedMonitoringResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DisableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .disableEnhancedMonitoring(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

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
              .trace(context.addJson("response", r._2.asJson).context)(
                "Logging response"
              )
              .as(response)
        )

        response.traverse { case (updated, response) =>
          streamsRef.set(updated).as(response)
        }
      }
  }

  def enableEnhancedMonitoring(
      req: EnableEnhancedMonitoringRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, EnableEnhancedMonitoringResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing EnableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .enableEnhancedMonitoring(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

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
              .trace(context.addJson("response", r._2.asJson).context)(
                "Logging response"
              )
              .as(response)
        )

        response.traverse { case (updated, response) =>
          streamsRef.set(updated).as(response)
        }
      }
  }

  def listShards(
      req: ListShardsRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, ListShardsResponse]] =
    logger.debug(context.context)(
      "Processing ListShards request"
    ) *>
      logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.listShards.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .listShards(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
        logger
          .warn(context.context)(
            "Rate limit exceeded for ListShards"
          )
          .as(Left(LimitExceededException("Limit exceeded for ListShards")))
      )

  def listStreamConsumers(
      req: ListStreamConsumersRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, ListStreamConsumersResponse]] = {
    val ctx = context + ("streamArn" -> req.streamArn)
    logger.debug(ctx.context)(
      "Processing ListStreamConsumers request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.listStreamConsumers.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .listStreamConsumers(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
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
  }

  def listStreams(
      req: ListStreamsRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, ListStreamsResponse]] =
    logger.debug(context.context)(
      "Processing ListStreams request"
    ) *>
      logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.listStreams.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .listStreams(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
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

  def listTagsForStream(
      req: ListTagsForStreamRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, ListTagsForStreamResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing ListTagsForStream request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.listTagsForStream.tryAcquireRelease(
        streamsRef.get.flatMap { streams =>
          val response = req
            .listTagsForStream(streams)
            .toEither
            .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
        },
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
  }

  def startStreamEncryption(
      req: StartStreamEncryptionRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing StartStreamEncryption request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .startStreamEncryption(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

        response
          .fold(
            e =>
              logger
                .warn(ctx.context, e)(
                  "Starting stream encryption was unuccessful"
                ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully started stream encryption"
              )
          ) *> response.traverse(updated =>
          streamsRef.set(updated) *>
            supervisor
              .supervise(
                logger.debug(context.context)(
                  s"Delaying setting the stream to active for ${config.startStreamEncryptionDuration.toString}"
                ) *>
                  IO.sleep(config.startStreamEncryptionDuration) *>
                  logger.debug(context.context)(
                    s"Setting the stream to active"
                  ) *>
                  streamsRef
                    .set(
                      updated.findAndUpdateStream(req.streamName)(x =>
                        x.copy(streamStatus = StreamStatus.ACTIVE)
                      )
                    )
              )
              .void
        )
      }
  }

  def stopStreamEncryption(
      req: StopStreamEncryptionRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing StopStreamEncryption request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .stopStreamEncryption(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

        response
          .fold(
            e =>
              logger
                .warn(ctx.context, e)(
                  "Stopping stream encryption was unuccessful"
                ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully stopped stream encryption"
              )
          ) *> response.traverse(updated =>
          streamsRef.set(updated) *>
            supervisor
              .supervise(
                logger.debug(context.context)(
                  s"Delaying setting the stream to active for ${config.stopStreamEncryptionDuration.toString}"
                ) *>
                  IO.sleep(config.stopStreamEncryptionDuration) *>
                  logger.debug(context.context)(
                    s"Setting the stream to active"
                  ) *>
                  streamsRef
                    .set(
                      updated.findAndUpdateStream(req.streamName)(x =>
                        x.copy(streamStatus = StreamStatus.ACTIVE)
                      )
                    )
              )
              .void
        )
      }
  }

  def getShardIterator(
      req: GetShardIteratorRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, GetShardIteratorResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing GetShardIterator request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .getShardIterator(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

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
                .trace(ctx.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
      }
  }
  def getRecords(
      req: GetRecordsRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, GetRecordsResponse]] =
    logger.debug(context.context)(
      "Processing GetRecords request"
    ) *>
      logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      streamsRef.get.flatMap { streams =>
        val response = req
          .getRecords(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)

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
                .trace(context.addJson("response", r.asJson).context)(
                  "Logging response"
                )
                .as(response)
          )
      }

  def putRecord(
      req: PutRecordRequest,
      context: LoggingContext
  ): IO[Either[KinesisMockException, PutRecordResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    for {
      _ <- logger.debug(ctx.context)("Processing PutRecord request")
      _ <- logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      )
      streams <- streamsRef.get
      shardSemaphores <- shardsSemaphoresRef.get
      put <- req
        .putRecord(streams, shardSemaphores)
        .map(_.toEither.leftMap(KinesisMockException.aggregate))
      _ <- put.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting record was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put record"
          ) *> logger
            .trace(ctx.addJson("response", r._2.asJson).context)(
              "Logging response"
            )
      )
      _ <- put.traverse { case (updated, _) => streamsRef.set(updated) }
      res = put.map(_._2)
    } yield res
  }

  def putRecords(
      req: PutRecordsRequest,
      context: LoggingContext
  )(implicit
      P: Parallel[IO]
  ): IO[Either[KinesisMockException, PutRecordsResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    for {
      _ <- logger.debug(ctx.context)("Processing PutRecords request")
      _ <- logger.trace(context.addJson("request", req.asJson).context)(
        "Logging request"
      )
      streams <- streamsRef.get
      shardSemaphores <- shardsSemaphoresRef.get
      put <- req
        .putRecords(streams, shardSemaphores)
        .map(_.toEither.leftMap(KinesisMockException.aggregate))
      _ <- put.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting records was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put records"
          ) *> logger
            .trace(ctx.addJson("response", r._2.asJson).context)(
              "Logging response"
            )
      )
      _ <- put.traverse { case (updated, _) => streamsRef.set(updated) }
      res = put.map(_._2)
    } yield res
  }

  def mergeShards(
      req: MergeShardsRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing MergeShards request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.mergeShards.tryAcquireRelease(
        for {
          streams <- streamsRef.get
          shardSemaphores <- shardsSemaphoresRef.get
          result <- req
            .mergeShards(streams, shardSemaphores)
            .map(_.toEither.leftMap(KinesisMockException.aggregate))
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
          res <- result.traverse { case (updated, newShardsSemaphoreKey) =>
            streamsRef.set(updated) *>
              Semaphore[IO](1).flatMap(semaphore =>
                shardsSemaphoresRef.update(shardsSemaphore =>
                  shardsSemaphore ++ List(newShardsSemaphoreKey -> semaphore)
                )
              ) *>
              supervisor
                .supervise(
                  logger.debug(context.context)(
                    s"Delaying setting the stream to active for ${config.mergeShardsDuration.toString}"
                  ) *>
                    IO.sleep(config.mergeShardsDuration) *>
                    logger.debug(context.context)(
                      s"Setting the stream to active"
                    ) *>
                    streamsRef
                      .set(
                        updated.findAndUpdateStream(req.streamName)(x =>
                          x.copy(streamStatus = StreamStatus.ACTIVE)
                        )
                      )
                )
                .void
          }
        } yield res,
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for MergeShards"
          )
          .as(Left(LimitExceededException("Limit Exceeded for MergeShards")))
      )
  }

  def splitShard(
      req: SplitShardRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing SplitShard request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *>
      semaphores.splitShard.tryAcquireRelease(
        for {
          streams <- streamsRef.get
          shardSemaphores <- shardsSemaphoresRef.get
          result <- req
            .splitShard(streams, shardSemaphores, config.shardLimit)
            .map(_.toEither.leftMap(KinesisMockException.aggregate))
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
          res <- result.traverse { case (updated, newShardsSemaphoreKeys) =>
            streamsRef.set(updated) *>
              Semaphore[IO](1)
                .flatMap(x => Semaphore[IO](1).map(y => List(x, y)))
                .flatMap(semaphores =>
                  shardsSemaphoresRef.update(shardsSemaphore =>
                    shardsSemaphore ++ newShardsSemaphoreKeys.zip(semaphores)
                  )
                ) *>
              supervisor
                .supervise(
                  logger.debug(context.context)(
                    s"Delaying setting the stream to active for ${config.splitShardDuration.toString}"
                  ) *>
                    IO.sleep(config.splitShardDuration) *>
                    logger.debug(context.context)(
                      s"Setting the stream to active"
                    ) *>
                    streamsRef
                      .set(
                        updated.findAndUpdateStream(req.streamName)(x =>
                          x.copy(streamStatus = StreamStatus.ACTIVE)
                        )
                      )
                )
                .void
          }
        } yield res,
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for MergeShards"
          )
          .as(Left(LimitExceededException("Limit Exceeded for SplitShard")))
      )
  }

  def updateShardCount(
      req: UpdateShardCountRequest,
      context: LoggingContext
  )(implicit
      T: Temporal[IO]): IO[Either[KinesisMockException, Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing UpdateShardCount request"
    ) *>
      logger.trace(ctx.addJson("request", req.asJson).context)(
        "Logging request"
      ) *> (for {
        streams <- streamsRef.get
        shardSemaphores <- shardsSemaphoresRef.get
        result <- req
          .updateShardCount(streams, shardSemaphores, config.shardLimit)
          .map(_.toEither.leftMap(KinesisMockException.aggregate))
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
        res <- result.traverse { case (updated, newShardsSemaphoreKeys) =>
          streamsRef.set(updated) *>
            Semaphore[IO](1)
              .flatMap(x => Semaphore[IO](1).map(y => List(x, y)))
              .flatMap(semaphores =>
                shardsSemaphoresRef.update(shardsSemaphore =>
                  shardsSemaphore ++ newShardsSemaphoreKeys.zip(semaphores)
                )
              ) *>
            supervisor
              .supervise(
                logger.debug(context.context)(
                  s"Delaying setting the stream to active for ${config.updateShardCountDuration.toString}"
                ) *>
                  IO.sleep(config.updateShardCountDuration) *>
                  logger.debug(context.context)(
                    s"Setting the stream to active"
                  ) *>
                  streamsRef
                    .set(
                      updated.findAndUpdateStream(req.streamName)(x =>
                        x.copy(streamStatus = StreamStatus.ACTIVE)
                      )
                    )
              )
              .void
        }
      } yield res)
  }

}

object Cache {
  def apply(
      config: CacheConfig
  )(implicit): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    shardsSemaphoresRef <- Ref.of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](
      Map.empty
    )
    semaphores <- CacheSemaphores.create
    supervisorResource = Supervisor[IO]
    cache <- supervisorResource.use(supervisor =>
      IO(new Cache(ref, shardsSemaphoresRef, semaphores, config, supervisor))
    )
  } yield cache
}
