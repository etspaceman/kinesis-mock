package kinesis.mock
package api

import scala.concurrent.duration._

import java.time.Instant

import cats.Parallel
import cats.data.Validated._
import cats.effect.{Concurrent, IO}
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref
import cats.effect.implicits._
import cats.effect.std.Semaphore

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateShardCount.html
final case class UpdateShardCountRequest(
    scalingType: ScalingType,
    streamName: StreamName,
    targetShardCount: Int
) {
  def updateShardCount(
      streamsRef: Ref[IO, Streams],
      shardSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]],
      shardLimit: Int
  )(implicit): IO[ValidatedResponse[Unit]] =
    streamsRef.get.flatMap { streams =>
      val now = Instant.now()
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen { stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                if (targetShardCount > stream.shards.size * 2)
                  InvalidArgumentException(
                    "Cannot update shard count beyond 2x current shard count"
                  ).invalidNel
                else if (targetShardCount < stream.shards.size / 2)
                  InvalidArgumentException(
                    "Cannot update shard count below 50% of the current shard count"
                  ).invalidNel
                else if (targetShardCount > 10000)
                  InvalidArgumentException(
                    "Cannot scale a stream beyond 10000 shards"
                  ).invalidNel
                else if (
                  streams.streams.values
                    .map(_.shards.size)
                    .sum + (targetShardCount - stream.shards.size) > shardLimit
                )
                  LimitExceededException(
                    "Operation would result more shards than the configured shard limit for this account"
                  ).invalidNel
                else Valid(targetShardCount),
                if (
                  stream.shardCountUpdates.count(ts =>
                    ts.toEpochMilli > now
                      .minusMillis(1.day.toMillis)
                      .toEpochMilli
                  ) >= 10
                )
                  LimitExceededException(
                    "Cannot run UpdateShardCount more than 10 times in a 24 hour period"
                  ).invalidNel
                else Valid(())
              ).mapN((_, _, _) => stream)
            }
        )
        .traverse { stream =>
          shardSemaphoresRef.get.flatMap { shardSemaphores =>
            val shards = stream.shards.toList
            val semaphoreKeys = shards.map { case (shard, _) =>
              ShardSemaphoresKey(streamName, shard)
            }
            val semaphores = shardSemaphores.toList.filter { case (key, _) =>
              semaphoreKeys.contains(key)
            }

            for {
              _ <- semaphores.parTraverse { case (_, semaphore) =>
                semaphore.acquire
              }
              oldShards = shards.map { case (shard, data) =>
                (
                  shard.copy(
                    closedTimestamp = Some(now),
                    sequenceNumberRange = shard.sequenceNumberRange
                      .copy(endingSequenceNumber =
                        Some(SequenceNumber.shardEnd)
                      )
                  ),
                  data
                )
              }
              newShards = Shard.newShards(
                targetShardCount,
                now,
                oldShards.map(_._1.shardId.index).max + 1
              )
              _ <- streamsRef.update(x =>
                x.updateStream(
                  stream.copy(
                    shards = newShards ++ oldShards,
                    streamStatus = StreamStatus.UPDATING
                  )
                )
              )
              newShardSemaphores <- newShards.keys.toList.traverse(shard =>
                Semaphore[IO](1).map(semaphore =>
                  ShardSemaphoresKey(streamName, shard) -> semaphore
                )
              )
              _ <- shardSemaphoresRef.update(x => x ++ newShardSemaphores)
              res <- semaphores.parTraverse_ { case (_, semaphore) =>
                semaphore.release
              }
            } yield res
          }
        }
    }
}

object UpdateShardCountRequest {
  implicit val updateShardCountRequestCirceEncoder
      : Encoder[UpdateShardCountRequest] =
    Encoder.forProduct3("ScalingType", "StreamName", "TargetShardCount")(x =>
      (x.scalingType, x.streamName, x.targetShardCount)
    )

  implicit val updateShardCountRequestCirceDecoder
      : Decoder[UpdateShardCountRequest] = x =>
    for {
      scalingType <- x.downField("ScalingType").as[ScalingType]
      streamName <- x.downField("StreamName").as[StreamName]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    } yield UpdateShardCountRequest(scalingType, streamName, targetShardCount)

  implicit val updateShardCountRequestEq: Eq[UpdateShardCountRequest] =
    Eq.fromUniversalEquals
}
