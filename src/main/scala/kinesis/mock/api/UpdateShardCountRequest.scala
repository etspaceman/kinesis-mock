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
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    targetShardCount: Int
) {
  def updateShardCount(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[UpdateShardCountResponse]] =
    streamsRef.modify { streams =>
      val now = Instant.now()
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
                .flatMap { stream =>
                  (
                    CommonValidations.isStreamActive(arn, streams),
                    if (
                      targetShardCount > stream.shards.keys.count(_.isOpen) * 2
                    )
                      InvalidArgumentException(
                        "Cannot update shard count beyond 2x current shard count"
                      ).asLeft
                    else if (
                      targetShardCount < stream.shards.keys.count(_.isOpen) / 2
                    )
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
              val openShards = stream.shards.toVector.filter(_._1.isOpen)
              val scalingUp = openShards.size < targetShardCount

              val newStreamData = if (scalingUp) {
                openShards.foldLeft(stream) {
                  case (streamData, (oldShard, oldShardData)) =>
                    SplitShardRequest.splitShard(
                      (oldShard.hashKeyRange.startingHashKey + ((oldShard.hashKeyRange.endingHashKey - oldShard.hashKeyRange.startingHashKey) / 2))
                        .toString(),
                      oldShard,
                      oldShardData,
                      streamData
                    )
                }
              } else {
                openShards.foldLeft(stream) {
                  case (streamData, (oldShard, oldShardData)) =>
                    streamData.shards
                      .find { case (x, _) =>
                        x.hashKeyRange.isAdjacent(oldShard.hashKeyRange)
                      }
                      .fold(
                        streamData
                      ) { case (adjacentShard, adjacentData) =>
                        MergeShardsRequest.mergeShards(
                          streamData,
                          adjacentShard,
                          adjacentData,
                          oldShard,
                          oldShardData
                        )
                      }
                }
              }

              (
                streams.updateStream(newStreamData),
                UpdateShardCountResponse(
                  openShards.length,
                  name,
                  targetShardCount
                )
              )
            }
        }
        .sequenceWithDefault(streams)
    }
}

object UpdateShardCountRequest {
  implicit val updateShardCountRequestCirceEncoder
      : circe.Encoder[UpdateShardCountRequest] =
    circe.Encoder.forProduct4(
      "ScalingType",
      "StreamName",
      "StreamARN",
      "TargetShardCount"
    )(x => (x.scalingType, x.streamName, x.streamArn, x.targetShardCount))

  implicit val updateShardCountRequestCirceDecoder
      : circe.Decoder[UpdateShardCountRequest] = x =>
    for {
      scalingType <- x.downField("ScalingType").as[ScalingType]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    } yield UpdateShardCountRequest(
      scalingType,
      streamName,
      streamArn,
      targetShardCount
    )

  implicit val updateShardCountRequestEncoder
      : Encoder[UpdateShardCountRequest] = Encoder.derive
  implicit val updateShardCountRequestDecoder
      : Decoder[UpdateShardCountRequest] = Decoder.derive

  implicit val updateShardCountRequestEq: Eq[UpdateShardCountRequest] =
    Eq.fromUniversalEquals
}
