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

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_MergeShards.html
final case class MergeShardsRequest(
    adjacentShardToMerge: String,
    shardToMerge: String,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
):
  def mergeShards(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    Utils.now.flatMap { now =>
      streamsRef.modify(streams =>
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
                      CommonValidations.validateShardId(shardToMerge),
                      CommonValidations.validateShardId(adjacentShardToMerge),
                      CommonValidations
                        .findShard(adjacentShardToMerge, stream)
                        .flatMap { case (adjacentShard, adjacentData) =>
                          CommonValidations.isShardOpen(adjacentShard).flatMap {
                            _ =>
                              CommonValidations
                                .findShard(shardToMerge, stream)
                                .flatMap { case (shard, shardData) =>
                                  CommonValidations.isShardOpen(shard).flatMap {
                                    _ =>
                                      if adjacentShard.hashKeyRange
                                          .isAdjacent(shard.hashKeyRange)
                                      then
                                        Right(
                                          (
                                            (adjacentShard, adjacentData),
                                            (shard, shardData)
                                          )
                                        )
                                      else
                                        InvalidArgumentException(
                                          "Provided shards are not adjacent"
                                        ).asLeft
                                  }
                                }
                          }
                        }
                    ).mapN {
                      case (
                            _,
                            _,
                            _,
                            ((adjacentShard, adjacentData), (shard, shardData))
                          ) =>
                        (
                          stream,
                          (adjacentShard, adjacentData),
                          (shard, shardData)
                        )
                    }
                  }
              )
              .map {
                case (
                      stream,
                      (adjacentShard, adjacentData),
                      (shard, shardData)
                    ) =>
                  (
                    streams.updateStream(
                      MergeShardsRequest.mergeShards(
                        stream,
                        adjacentShard,
                        adjacentData,
                        shard,
                        shardData,
                        now
                      )
                    ),
                    ()
                  )
              }
          }
          .sequenceWithDefault(streams)
      )
    }

object MergeShardsRequest:

  def mergeShards(
      stream: StreamData,
      adjacentShard: Shard,
      adjacentData: Vector[KinesisRecord],
      shard: Shard,
      shardData: Vector[KinesisRecord],
      now: Instant
  ): StreamData =
    val newShardIndex =
      stream.shards.keys.map(_.shardId.index).max + 1
    val newShard: (Shard, Vector[KinesisRecord]) = Shard(
      Some(adjacentShard.shardId.shardId),
      None,
      now,
      HashKeyRange(
        adjacentShard.hashKeyRange.endingHashKey
          .max(shard.hashKeyRange.endingHashKey),
        adjacentShard.hashKeyRange.startingHashKey
          .min(shard.hashKeyRange.startingHashKey)
      ),
      Some(shard.shardId.shardId),
      SequenceNumberRange(
        None,
        if adjacentShard.sequenceNumberRange.startingSequenceNumber.numericValue < shard.sequenceNumberRange.startingSequenceNumber.numericValue
        then adjacentShard.sequenceNumberRange.startingSequenceNumber
        else shard.sequenceNumberRange.startingSequenceNumber
      ),
      ShardId.create(newShardIndex)
    ) -> Vector.empty

    val oldShards: Vector[(Shard, Vector[KinesisRecord])] = Vector(
      adjacentShard.copy(
        closedTimestamp = Some(now),
        sequenceNumberRange = adjacentShard.sequenceNumberRange
          .copy(endingSequenceNumber = Some(SequenceNumber.shardEnd))
      ) -> adjacentData,
      shard.copy(
        closedTimestamp = Some(now),
        sequenceNumberRange = shard.sequenceNumberRange
          .copy(endingSequenceNumber = Some(SequenceNumber.shardEnd))
      ) -> shardData
    )
    stream.copy(
      shards = stream.shards.filterNot { case (s, _) =>
        s.shardId == adjacentShard.shardId || s.shardId == shard.shardId
      }
        ++ (oldShards :+ newShard),
      streamStatus = StreamStatus.UPDATING
    )

  given mergeShardsRequestCirceEncoder: circe.Encoder[MergeShardsRequest] =
    circe.Encoder.forProduct4(
      "AdjacentShardToMerge",
      "ShardToMerge",
      "StreamName",
      "StreamARN"
    )(x => (x.adjacentShardToMerge, x.shardToMerge, x.streamName, x.streamArn))

  given mergeShardsRequestCirceDecoder: circe.Decoder[MergeShardsRequest] =
    x =>
      for
        adjacentShardToMerge <- x.downField("AdjacentShardToMerge").as[String]
        shardToMerge <- x.downField("ShardToMerge").as[String]
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      yield MergeShardsRequest(
        adjacentShardToMerge,
        shardToMerge,
        streamName,
        streamArn
      )
  given mergeShardsRequestEncoder: Encoder[MergeShardsRequest] =
    Encoder.derive
  given mergeShardsRequestDecoder: Decoder[MergeShardsRequest] =
    Decoder.derive
  given Eq[MergeShardsRequest] =
    Eq.fromUniversalEquals
