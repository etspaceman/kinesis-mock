package kinesis.mock
package api

import scala.concurrent.duration._

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateShardCount.html
final case class UpdateShardCountRequest(
    scalingType: ScalingType,
    streamName: StreamName,
    targetShardCount: Int
) {
  def updateShardCount(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[UpdateShardCountResponse]] =
    streamsRef.modify { streams =>
      val streamArn = StreamArn(awsRegion, streamName, awsAccountId)
      val now = Instant.now()
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamArn, streams)
            .flatMap { stream =>
              (
                CommonValidations.isStreamActive(streamArn, streams),
                if (targetShardCount > stream.shards.keys.count(_.isOpen) * 2)
                  InvalidArgumentException(
                    "Cannot update shard count beyond 2x current shard count"
                  ).asLeft
                else if (targetShardCount < stream.shards.keys.count(_.isOpen) / 2)
                  InvalidArgumentException(
                    "Cannot update shard count below 50% of the current shard count"
                  ).asLeft
                else if (targetShardCount > 10000)
                  InvalidArgumentException(
                    "Cannot scale a stream beyond 10000 shards"
                  ).asLeft
                else if (
                  streams.streams.values
                    .map(_.shards.size)
                    .sum + (targetShardCount - stream.shards.size) > shardLimit
                )
                  LimitExceededException(
                    "Operation would result more shards than the configured shard limit for this account"
                  ).asLeft
                else Right(targetShardCount),
                if (
                  stream.shardCountUpdates.count(ts =>
                    ts.toEpochMilli > now
                      .minusMillis(1.day.toMillis)
                      .toEpochMilli
                  ) >= 10
                )
                  LimitExceededException(
                    "Cannot run UpdateShardCount more than 10 times in a 24 hour period"
                  ).asLeft
                else Right(())
              ).mapN((_, _, _) => stream)
            }
        )
        .map { stream =>
          val shards = stream.shards.toVector
          val oldShards = shards.map { case (shard, data) =>
            (
              shard.copy(
                closedTimestamp = Some(now),
                sequenceNumberRange = shard.sequenceNumberRange
                  .copy(endingSequenceNumber = Some(SequenceNumber.shardEnd))
              ),
              data
            )
          }
          val newShards = Shard.newShards(
            targetShardCount,
            now,
            oldShards.map(_._1.shardId.index).max + 1
          )
          val combined = newShards ++ oldShards
          (
            streams.updateStream(
              stream.copy(
                shards = combined,
                streamStatus = StreamStatus.UPDATING
              )
            ),
            UpdateShardCountResponse(
              shards.length,
              streamName,
              targetShardCount
            )
          )
        }
        .sequenceWithDefault(streams)
    }
}

object UpdateShardCountRequest {
  implicit val updateShardCountRequestCirceEncoder
      : circe.Encoder[UpdateShardCountRequest] =
    circe.Encoder.forProduct3("ScalingType", "StreamName", "TargetShardCount")(
      x => (x.scalingType, x.streamName, x.targetShardCount)
    )

  implicit val updateShardCountRequestCirceDecoder
      : circe.Decoder[UpdateShardCountRequest] = x =>
    for {
      scalingType <- x.downField("ScalingType").as[ScalingType]
      streamName <- x.downField("StreamName").as[StreamName]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    } yield UpdateShardCountRequest(scalingType, streamName, targetShardCount)

  implicit val updateShardCountRequestEncoder
      : Encoder[UpdateShardCountRequest] = Encoder.derive
  implicit val updateShardCountRequestDecoder
      : Decoder[UpdateShardCountRequest] = Decoder.derive

  implicit val updateShardCountRequestEq: Eq[UpdateShardCountRequest] =
    Eq.fromUniversalEquals
}
