package kinesis.mock
package api

import scala.concurrent.duration._

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.effect.IO
import cats.effect.concurrent.Semaphore
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateShardCount.html
final case class UpdateShardCountRequest(
    scalingType: ScalingType,
    streamName: StreamName,
    targetShardCount: Int
) {
  def updateShardCount(
      streams: Streams,
      shardSemaphores: Map[ShardSemaphoresKey, Semaphore[IO]],
      shardLimit: Int
  ): IO[
    ValidatedNel[KinesisMockException, (Streams, List[ShardSemaphoresKey])]
  ] = {
    val now = Instant.now()
    CommonValidations
      .findStream(streamName, streams)
      .andThen { stream =>
        (
          CommonValidations.validateStreamName(streamName),
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
            stream.shardCountUpdates
              .filter(ts =>
                ts.toEpochMilli > now.minusMillis(1.day.toMillis).toEpochMilli
              )
              .length >= 10
          )
            LimitExceededException(
              "Cannot run UpdateShardCount more than 10 times in a 24 hour period"
            ).invalidNel
          else Valid(())
        ).mapN((_, _, _, _) => stream)
      }
      .traverse { stream =>
        val shards = stream.shards

        shards.toList
          .traverse { case (shard, data) =>
            shardSemaphores(ShardSemaphoresKey(streamName, shard)).acquire
              .map { _ =>
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
          }
          .map { oldShards =>
            val newShards = Shard.newShards(
              targetShardCount,
              now,
              oldShards.map(_._1.shardId.index).max + 1
            )

            (
              streams.updateStream(
                stream.copy(
                  shards = newShards ++ oldShards,
                  streamStatus = StreamStatus.UPDATING
                )
              ),
              newShards.keys.toList
                .map(shard => ShardSemaphoresKey(streamName, shard))
            )
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
