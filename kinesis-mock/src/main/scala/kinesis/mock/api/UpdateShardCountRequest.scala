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
package api

import scala.concurrent.duration.*

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateShardCount.html
final case class UpdateShardCountRequest(
    scalingType: ScalingType,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    targetShardCount: Int
):
  def updateShardCount(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[UpdateShardCountResponse]] =
    Utils.now.flatMap { now =>
      streamsRef.modify { streams =>
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
                      if targetShardCount > stream.shards.keys
                          .count(_.isOpen) * 2
                      then
                        InvalidArgumentException(
                          "Cannot update shard count beyond 2x current shard count"
                        ).asLeft
                      else if targetShardCount < stream.shards.keys
                          .count(_.isOpen) / 2
                      then
                        InvalidArgumentException(
                          "Cannot update shard count below 50% of the current shard count"
                        ).asLeft
                      else if targetShardCount > 10000 then
                        InvalidArgumentException(
                          "Cannot scale a stream beyond 10000 shards"
                        ).asLeft
                      else if streams.streams.values
                          .map(_.shards.size)
                          .sum + (targetShardCount - stream.shards.size) > shardLimit
                      then
                        LimitExceededException(
                          "Operation would result more shards than the configured shard limit for this account"
                        ).asLeft
                      else Right(targetShardCount),
                      if stream.shardCountUpdates.count(ts =>
                          ts.toEpochMilli > now
                            .minusMillis(1.day.toMillis)
                            .toEpochMilli
                        ) >= 10
                      then
                        LimitExceededException(
                          "Cannot run UpdateShardCount more than 10 times in a 24 hour period"
                        ).asLeft
                      else Right(())
                    ).mapN((_, _, _) => stream)
                  }
              )
              .map { stream =>
                val openShards = stream.shards.toList.filter(_._1.isOpen)
                val scalingUp = openShards.size < targetShardCount

                val newStreamData =
                  if scalingUp then
                    UpdateShardCountRequest.splitShards(
                      stream,
                      openShards,
                      targetShardCount,
                      now
                    )
                  else
                    UpdateShardCountRequest.mergeShards(
                      stream,
                      openShards,
                      targetShardCount,
                      now
                    )

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

object UpdateShardCountRequest:
  @annotation.tailrec
  def mergeShards(
      streamData: StreamData,
      openShards: List[(Shard, Vector[KinesisRecord])],
      targetShardCount: Int,
      now: Instant
  ): StreamData = openShards match
    case _
        if streamData.shards.toList.count(_._1.isOpen) === targetShardCount =>
      streamData
    case Nil | _ :: Nil => streamData
    case h :: t         =>
      val (oldShard, oldShardData) = h
      val (newStreamData, newOpenShards) = t
        .find { case (x, _) =>
          x.hashKeyRange.isAdjacent(oldShard.hashKeyRange)
        }
        .fold((streamData, t)) { case (adjacentShard, adjacentData) =>
          (
            MergeShardsRequest.mergeShards(
              streamData,
              adjacentShard,
              adjacentData,
              oldShard,
              oldShardData,
              now
            ),
            t.filterNot(_._1.shardId == adjacentShard.shardId)
          )
        }
      mergeShards(newStreamData, newOpenShards, targetShardCount, now)

  @annotation.tailrec
  def splitShards(
      streamData: StreamData,
      openShards: List[(Shard, Vector[KinesisRecord])],
      targetShardCount: Int,
      now: Instant
  ): StreamData = openShards match
    case _
        if streamData.shards.toList.count(_._1.isOpen) === targetShardCount =>
      streamData
    case Nil    => streamData
    case h :: t =>
      val (oldShard, oldShardData) = h
      splitShards(
        SplitShardRequest.splitShard(
          (oldShard.hashKeyRange.startingHashKey + ((oldShard.hashKeyRange.endingHashKey - oldShard.hashKeyRange.startingHashKey) / 2))
            .toString(),
          oldShard,
          oldShardData,
          streamData,
          now
        ),
        t,
        targetShardCount,
        now
      )

  given updateShardCountRequestCirceEncoder
      : circe.Encoder[UpdateShardCountRequest] =
    circe.Encoder.forProduct4(
      "ScalingType",
      "StreamName",
      "StreamARN",
      "TargetShardCount"
    )(x => (x.scalingType, x.streamName, x.streamArn, x.targetShardCount))

  given updateShardCountRequestCirceDecoder
      : circe.Decoder[UpdateShardCountRequest] = x =>
    for
      scalingType <- x.downField("ScalingType").as[ScalingType]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    yield UpdateShardCountRequest(
      scalingType,
      streamName,
      streamArn,
      targetShardCount
    )

  given updateShardCountRequestEncoder: Encoder[UpdateShardCountRequest] =
    Encoder.derive
  given updateShardCountRequestDecoder: Decoder[UpdateShardCountRequest] =
    Decoder.derive

  given Eq[UpdateShardCountRequest] =
    Eq.fromUniversalEquals
