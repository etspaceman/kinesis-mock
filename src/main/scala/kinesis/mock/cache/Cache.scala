package kinesis.mock
package cache

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._

import kinesis.mock.api._
import kinesis.mock.models._
import kinesis.mock.syntax.semaphore._

class Cache private (
    ref: Ref[IO, Streams],
    semaphores: CacheSemaphores,
    config: CacheConfig
) {

  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquireRelease(
      ref.get.flatMap(streams =>
        req
          .addTagsToStream(streams)
          .traverse(ref.set)
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
      ref.get.flatMap(streams =>
        req
          .removeTagsFromStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(ref.set)
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
        streams <- ref.get
        res <- req
          .createStream(
            streams,
            config.shardLimit,
            config.awsRegion,
            config.awsAccountId
          )
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(updated =>
            ref.set(updated) *>
              (IO.sleep(config.createStreamDuration) *>
                // Update the stream as ACTIVE after a small, configured delay
                ref
                  .set(
                    updated.findAndUpdateStream(req.streamName)(x =>
                      x.copy(streamStatus = StreamStatus.ACTIVE)
                    )
                  )
                  .start
                  .void)
          )
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
        streams <- ref.get
        res <- req
          .deleteStream(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse(updated =>
            ref.set(updated) *>
              (IO.sleep(config.deleteStreamDuration) *>
                // Remove the stream after a small, configured delay
                ref.set(
                  updated.removeStream(req.streamName)
                )).start.void
          )
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
    ref.get.flatMap(streams =>
      req
        .decreaseStreamRetention(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(ref.set)
    )

  def increaseStreamRetention(
      req: IncreaseStreamRetentionRequest
  ): IO[Either[KinesisMockException, Unit]] =
    ref.get.flatMap(streams =>
      req
        .increaseStreamRetention(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(ref.set)
    )

  def describeLimits: IO[Either[KinesisMockException, DescribeLimitsResponse]] =
    semaphores.describeLimits.tryAcquireRelease(
      ref.get.map(streams =>
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
      ref.get.map(streams =>
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
      ref.get.map(streams =>
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
      ref.get.flatMap(streams =>
        req
          .registerStreamConsumer(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, response) =>
            ref
              .set(updated)
              .flatMap { _ =>
                (IO.sleep(config.registerStreamConsumerDuration) *>
                  // Update the consumer as ACTIVE after a small, configured delay
                  updated.streams.values
                    .find(_.streamArn == req.streamArn)
                    .traverse(stream =>
                      ref.set(
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
      ref.get.flatMap(streams =>
        req
          .deregisterStreamConsumer(streams)
          .toEither
          .leftMap(KinesisMockException.aggregate)
          .traverse { case (updated, consumer) =>
            ref
              .set(updated)
              .flatMap { _ =>
                (IO.sleep(config.deregisterStreamConsumerDuration) *>
                  // Remove the consumer after a small, configured delay
                  updated.streams.values
                    .find(_.streamArn == req.streamArn)
                    .traverse(stream =>
                      ref.set(
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
      ref.get.map(streams =>
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
    ref.get.flatMap(streams =>
      req
        .disableEnhancedMonitoring(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse { case (updated, response) => ref.set(updated).as(response) }
    )

  def enableEnhancedMonitoring(
      req: EnableEnhancedMonitoringRequest
  ): IO[Either[KinesisMockException, EnableEnhancedMonitoringResponse]] =
    ref.get.flatMap(streams =>
      req
        .enableEnhancedMonitoring(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse { case (updated, response) => ref.set(updated).as(response) }
    )

  def listShards(
      req: ListShardsRequest
  ): IO[Either[KinesisMockException, ListShardsResponse]] =
    semaphores.listShards.tryAcquireRelease(
      ref.get.map(streams =>
        req.listShards(streams).toEither.leftMap(KinesisMockException.aggregate)
      ),
      IO.pure(Left(LimitExceededException("Limit exceeded for ListShards")))
    )

  def listStreamConsumers(
      req: ListStreamConsumersRequest
  ): IO[Either[KinesisMockException, ListStreamConsumersResponse]] =
    semaphores.listStreamConsumers.tryAcquireRelease(
      ref.get.map(streams =>
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
      ref.get.map(streams =>
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
      ref.get.map(streams =>
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
    ref.get.flatMap(streams =>
      req
        .startStreamEncryption(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(updated =>
          ref.set(updated) *>
            (IO.sleep(config.startStreamEncryptionDuration) *>
              // Update the stream as ACTIVE after a small, configured delay
              ref
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
    ref.get.flatMap(streams =>
      req
        .stopStreamEncryption(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
        .traverse(updated =>
          ref.set(updated) *>
            (IO.sleep(config.stopStreamEncryptionDuration) *>
              // Update the stream as ACTIVE after a small, configured delay
              ref
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
    ref.get.map(streams =>
      req
        .getShardIterator(streams)
        .toEither
        .leftMap(KinesisMockException.aggregate)
    )
  def getRecords(
      req: GetRecordsRequest
  ): IO[Either[KinesisMockException, GetRecordsResponse]] =
    ref.get.map(streams =>
      req.getRecords(streams).toEither.leftMap(KinesisMockException.aggregate)
    )

  def putRecord(
      req: PutRecordRequest
  ): IO[Either[KinesisMockException, PutRecordResponse]] = for {
    streams <- ref.get
    put <- req
      .putRecord(streams)
      .map(_.toEither.leftMap(KinesisMockException.aggregate))
    _ <- put.traverse { case (updated, _) => ref.set(updated) }
    res = put.map(_._2)
  } yield res
}

object Cache {
  def apply(config: CacheConfig)(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    semaphores <- CacheSemaphores.create
  } yield new Cache(ref, semaphores, config)
}
