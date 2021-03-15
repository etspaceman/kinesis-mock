package kinesis.mock
package cache

import cats.Parallel
import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import cats.syntax.all._

import kinesis.mock.api._
import kinesis.mock.models._
import kinesis.mock.syntax.semaphore._

class Cache private (
    streamsRef: Ref[IO, Streams],
    shardsSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]],
    semaphores: CacheSemaphores,
    config: CacheConfig
) {

  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquireRelease(
      streamsRef.get.flatMap(streams =>
        req
          .addTagsToStream(streams)
          .traverse(streamsRef.set)
          .map(_.toEither.leftMap(KinesisMockException.aggregate))
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for AddTagsToStream"
          )
        )
      )
    )

  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.removeTagsFromStream.tryAcquireRelease(
      streamsRef.get.flatMap(streams =>
        req
          .removeTagsFromStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(streamsRef.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RemoveTagsFromStream"
          )
        )
      )
    )

  def createStream(
      req: CreateStreamRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.createStream.tryAcquireRelease(
      for {
        streams <- streamsRef.get
        res <- req
          .createStream(
            streams,
            config.shardLimit,
            config.awsRegion,
            config.awsAccountId
          )
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, shardSemaphoreKeys) =>
            for {
              _ <- streamsRef.set(updated)
              newShardSemaphores <- shardSemaphoreKeys
                .traverse(key => Semaphore[IO](1).map(s => key -> s))
              _ <- shardsSemaphoresRef.update(shardSemaphores =>
                shardSemaphores ++ newShardSemaphores
              )
              r <- (IO.sleep(config.createStreamDuration) *>
                // Update the stream as ACTIVE after a small, configured delay
                streamsRef
                  .set(
                    updated.findAndUpdateStream(req.streamName)(x =>
                      x.copy(streamStatus = StreamStatus.ACTIVE)
                    )
                  )
                  .start
                  .void)
            } yield r

          }
      } yield res,
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for CreateStream"
          )
        )
      )
    )

  def deleteStream(
      req: DeleteStreamRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.deleteStream.tryAcquireRelease(
      for {
        streams <- streamsRef.get
        res <- req
          .deleteStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, shardSemaphoreKeys) =>
            for {
              _ <- streamsRef.set(updated)
              _ <- shardsSemaphoresRef.update(shardsSemaphores =>
                shardsSemaphores -- shardSemaphoreKeys
              )
              r <- (IO.sleep(config.deleteStreamDuration) *>
                // Remove the stream after a small, configured delay
                streamsRef.set(
                  updated.removeStream(req.streamName)
                )).start.void
            } yield r

          }
      } yield res,
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for CreateStream"
          )
        )
      )
    )

  def decreaseStreamRetention(
      req: DecreaseStreamRetentionRequest
  ): IO[Either[KinesisMockException, Unit]] =
    streamsRef.get.flatMap(streams =>
      req
        .decreaseStreamRetention(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(streamsRef.set)
    )

  def increaseStreamRetention(
      req: IncreaseStreamRetentionRequest
  ): IO[Either[KinesisMockException, Unit]] =
    streamsRef.get.flatMap(streams =>
      req
        .increaseStreamRetention(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(streamsRef.set)
    )

  def describeLimits: IO[Either[KinesisMockException, DescribeLimitsResponse]] =
    semaphores.describeLimits.tryAcquireRelease(
      streamsRef.get.map(streams =>
        Right(DescribeLimitsResponse.get(config.shardLimit, streams))
      ),
      IO.pure(
        Left(LimitExceededException("Rate limit exceeded for DescribeLimits"))
      )
    )

  def describeStream(
      req: DescribeStreamRequest
  ): IO[Either[KinesisMockException, DescribeStreamResponse]] =
    semaphores.describeStream.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .describeStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(LimitExceededException("Rate limit exceeded for DescribeStream"))
      )
    )

  def describeStreamSummary(
      req: DescribeStreamSummaryRequest
  ): IO[Either[KinesisMockException, DescribeStreamSummaryResponse]] =
    semaphores.describeStream.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .describeStreamSummary(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for DescribeStreamSummary"
          )
        )
      )
    )

  def registerStreamConsumer(
      req: RegisterStreamConsumerRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, RegisterStreamConsumerResponse]] =
    semaphores.registerStreamConsumer.tryAcquireRelease(
      streamsRef.get.flatMap(streams =>
        req
          .registerStreamConsumer(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, response) =>
            streamsRef
              .set(updated)
              .flatMap { _ =>
                (IO.sleep(config.registerStreamConsumerDuration) *>
                  // Update the consumer as ACTIVE after a small, configured delay
                  updated.streams.values
                    .find(_.streamArn == req.streamArn)
                    .traverse(stream =>
                      streamsRef.set(
                        updated.updateStream(
                          stream.copy(consumers =
                            stream.consumers + (response.consumer.consumerName -> response.consumer
                              .copy(consumerStatus = ConsumerStatus.ACTIVE))
                          )
                        )
                      )
                    )).start.void
              }
              .as(response)
          }
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RegisterStreamConsumer"
          )
        )
      )
    )

  def deregisterStreamConsumer(
      req: DeregisterStreamConsumerRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.deregisterStreamConsumer.tryAcquireRelease(
      streamsRef.get.flatMap(streams =>
        req
          .deregisterStreamConsumer(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, consumer) =>
            streamsRef
              .set(updated)
              .flatMap { _ =>
                (IO.sleep(config.deregisterStreamConsumerDuration) *>
                  // Remove the consumer after a small, configured delay
                  updated.streams.values
                    .find(_.streamArn == req.streamArn)
                    .traverse(stream =>
                      streamsRef.set(
                        updated.updateStream(
                          stream.copy(consumers =
                            stream.consumers - consumer.consumerName
                          )
                        )
                      )
                    )).start.void
              }
          }
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RegisterStreamConsumer"
          )
        )
      )
    )

  def describeStreamConsumer(
      req: DescribeStreamConsumerRequest
  ): IO[Either[KinesisMockException, DescribeStreamConsumerResponse]] =
    semaphores.describeStreamConsumer.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .describeStreamConsumer(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(
          LimitExceededException("Limit exceeded for DescribeStreamConsumer")
        )
      )
    )

  def disableEnhancedMonitoring(
      req: DisableEnhancedMonitoringRequest
  ): IO[Either[KinesisMockException, DisableEnhancedMonitoringResponse]] =
    streamsRef.get.flatMap(streams =>
      req
        .disableEnhancedMonitoring(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse { case (updated, response) =>
          streamsRef.set(updated).as(response)
        }
    )

  def enableEnhancedMonitoring(
      req: EnableEnhancedMonitoringRequest
  ): IO[Either[KinesisMockException, EnableEnhancedMonitoringResponse]] =
    streamsRef.get.flatMap(streams =>
      req
        .enableEnhancedMonitoring(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse { case (updated, response) =>
          streamsRef.set(updated).as(response)
        }
    )

  def listShards(
      req: ListShardsRequest
  ): IO[Either[KinesisMockException, ListShardsResponse]] =
    semaphores.listShards.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req.listShards(streams).toEither.leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(Left(LimitExceededException("Limit exceeded for ListShards")))
    )

  def listStreamConsumers(
      req: ListStreamConsumersRequest
  ): IO[Either[KinesisMockException, ListStreamConsumersResponse]] =
    semaphores.listStreamConsumers.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .listStreamConsumers(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(LimitExceededException("Limit exceeded for ListStreamConsumers"))
      )
    )

  def listStreams(
      req: ListStreamsRequest
  ): IO[Either[KinesisMockException, ListStreamsResponse]] =
    semaphores.listStreams.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .listStreams(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(LimitExceededException("Limit exceeded for ListStreams"))
      )
    )

  def listTagsForStream(
      req: ListTagsForStreamRequest
  ): IO[Either[KinesisMockException, ListTagsForStreamResponse]] =
    semaphores.listTagsForStream.tryAcquireRelease(
      streamsRef.get.map(streams =>
        req
          .listTagsForStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(
        Left(LimitExceededException("Limit exceeded for ListTagsForStream"))
      )
    )

  def startStreamEncryption(
      req: StartStreamEncryptionRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    streamsRef.get.flatMap(streams =>
      req
        .startStreamEncryption(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(updated =>
          streamsRef.set(updated) *>
            (IO.sleep(config.startStreamEncryptionDuration) *>
              // Update the stream as ACTIVE after a small, configured delay
              streamsRef
                .set(
                  updated.findAndUpdateStream(req.streamName)(x =>
                    x.copy(streamStatus = StreamStatus.ACTIVE)
                  )
                )
                .start
                .void)
        )
    )

  def stopStreamEncryption(
      req: StopStreamEncryptionRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    streamsRef.get.flatMap(streams =>
      req
        .stopStreamEncryption(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(updated =>
          streamsRef.set(updated) *>
            (IO.sleep(config.stopStreamEncryptionDuration) *>
              // Update the stream as ACTIVE after a small, configured delay
              streamsRef
                .set(
                  updated.findAndUpdateStream(req.streamName)(x =>
                    x.copy(streamStatus = StreamStatus.ACTIVE)
                  )
                )
                .start
                .void)
        )
    )

  def getShardIterator(
      req: GetShardIteratorRequest
  ): IO[Either[KinesisMockException, GetShardIteratorResponse]] =
    streamsRef.get.map(streams =>
      req
        .getShardIterator(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
    )
  def getRecords(
      req: GetRecordsRequest
  ): IO[Either[KinesisMockException, GetRecordsResponse]] =
    streamsRef.get.map(streams =>
      req.getRecords(streams).toEither.leftMap(KinesisMockException.aggregate)
    )

  def putRecord(
      req: PutRecordRequest
  ): IO[Either[KinesisMockException, PutRecordResponse]] = for {
    streams <- streamsRef.get
    shardSemaphores <- shardsSemaphoresRef.get
    put <- req
      .putRecord(streams, shardSemaphores)
      .map(_.toEither.leftMap(KinesisMockException.aggregate))
    _ <- put.traverse { case (updated, _) => streamsRef.set(updated) }
    res = put.map(_._2)
  } yield res

  def putRecords(
      req: PutRecordsRequest
  )(implicit
      P: Parallel[IO]
  ): IO[Either[KinesisMockException, PutRecordsResponse]] = for {
    streams <- streamsRef.get
    shardSemaphores <- shardsSemaphoresRef.get
    put <- req
      .putRecords(streams, shardSemaphores)
      .map(_.toEither.leftMap(KinesisMockException.aggregate))
    _ <- put.traverse { case (updated, _) => streamsRef.set(updated) }
    res = put.map(_._2)
  } yield res

  def mergeShards(
      req: MergeShardsRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.mergeShards.tryAcquireRelease(
      for {
        streams <- streamsRef.get
        shardSemaphores <- shardsSemaphoresRef.get
        result <- req
          .mergeShards(streams, shardSemaphores)
          .map(_.toEither.leftMap(KinesisMockException.aggregate))
        res <- result.traverse { case (updated, newShardsSemaphoreKey) =>
          streamsRef.set(updated) *>
            Semaphore[IO](1).flatMap(semaphore =>
              shardsSemaphoresRef.update(shardsSemaphore =>
                shardsSemaphore ++ List(newShardsSemaphoreKey -> semaphore)
              )
            )
          (IO.sleep(config.mergeShardsDuration) *>
            // Update the stream as ACTIVE after a small, configured delay
            streamsRef
              .set(
                updated.findAndUpdateStream(req.streamName)(x =>
                  x.copy(streamStatus = StreamStatus.ACTIVE)
                )
              )
              .start
              .void)
        }
      } yield res,
      IO.pure(Left(LimitExceededException("Limit Exceeded for MergeShards")))
    )

  def splitShard(
      req: SplitShardRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.splitShard.tryAcquireRelease(
      for {
        streams <- streamsRef.get
        shardSemaphores <- shardsSemaphoresRef.get
        result <- req
          .splitShard(streams, shardSemaphores, config.shardLimit)
          .map(_.toEither.leftMap(KinesisMockException.aggregate))
        res <- result.traverse { case (updated, newShardsSemaphoreKeys) =>
          streamsRef.set(updated) *>
            Semaphore[IO](1)
              .flatMap(x => Semaphore[IO](1).map(y => List(x, y)))
              .flatMap(semaphores =>
                shardsSemaphoresRef.update(shardsSemaphore =>
                  shardsSemaphore ++ newShardsSemaphoreKeys.zip(semaphores)
                )
              )
          (IO.sleep(config.splitShardDuration) *>
            // Update the stream as ACTIVE after a small, configured delay
            streamsRef
              .set(
                updated.findAndUpdateStream(req.streamName)(x =>
                  x.copy(streamStatus = StreamStatus.ACTIVE)
                )
              )
              .start
              .void)
        }
      } yield res,
      IO.pure(Left(LimitExceededException("Limit Exceeded for SplitShard")))
    )

  def updateShardCount(
      req: UpdateShardCountRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    for {
      streams <- streamsRef.get
      shardSemaphores <- shardsSemaphoresRef.get
      result <- req
        .updateShardCount(streams, shardSemaphores, config.shardLimit)
        .map(_.toEither.leftMap(KinesisMockException.aggregate))
      res <- result.traverse { case (updated, newShardsSemaphoreKeys) =>
        streamsRef.set(updated) *>
          Semaphore[IO](1)
            .flatMap(x => Semaphore[IO](1).map(y => List(x, y)))
            .flatMap(semaphores =>
              shardsSemaphoresRef.update(shardsSemaphore =>
                shardsSemaphore ++ newShardsSemaphoreKeys.zip(semaphores)
              )
            )
        (IO.sleep(config.updateShardCountDuration) *>
          // Update the stream as ACTIVE after a small, configured delay
          streamsRef
            .set(
              updated.findAndUpdateStream(req.streamName)(x =>
                x.copy(streamStatus = StreamStatus.ACTIVE)
              )
            )
            .start
            .void)
      }
    } yield res

}

object Cache {
  def apply(config: CacheConfig)(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    shardsSemaphoresRef <- Ref.of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](
      Map.empty
    )
    semaphores <- CacheSemaphores.create
  } yield new Cache(ref, shardsSemaphoresRef, semaphores, config)
}
