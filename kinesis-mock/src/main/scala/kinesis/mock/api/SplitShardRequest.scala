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

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SplitShard.html
final case class SplitShardRequest(
    newStartingHashKey: String,
    shardToSplit: String,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def splitShard(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
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
                    CommonValidations.validateShardId(shardToSplit),
                    if (!newStartingHashKey.matches("0|([1-9]\\d{0,38})")) {
                      InvalidArgumentException(
                        "NewStartingHashKey contains invalid characters"
                      ).asLeft
                    } else Right(newStartingHashKey),
                    if (
                      streams.streams.values
                        .map(_.shards.size)
                        .sum + 1 > shardLimit
                    )
                      LimitExceededException(
                        "Operation would exceed the configured shard limit for the account"
                      ).asLeft
                    else Right(()),
                    CommonValidations.findShard(shardToSplit, stream).flatMap {
                      case (shard, shardData) =>
                        CommonValidations.isShardOpen(shard).flatMap { _ =>
                          val newStartingHashKeyNumber =
                            BigInt(newStartingHashKey)
                          if (
                            newStartingHashKeyNumber >= shard.hashKeyRange.startingHashKey && newStartingHashKeyNumber <= shard.hashKeyRange.endingHashKey
                          )
                            Right((shard, shardData))
                          else
                            InvalidArgumentException(
                              s"NewStartingHashKey is not within the hash range shard ${shard.shardId}"
                            ).asLeft
                        }
                    }
                  ).mapN { case (_, _, _, _, (shard, shardData)) =>
                    (shard, shardData, stream)
                  }
                }
            )
            .map { case (shard, shardData, stream) =>
              (
                streams.updateStream(
                  SplitShardRequest.splitShard(
                    newStartingHashKey,
                    shard,
                    shardData,
                    stream
                  )
                ),
                ()
              )
            }
        }
        .sequenceWithDefault(streams)
    }
}

object SplitShardRequest {

  def splitShard(
      newStartingHashKey: String,
      shard: Shard,
      shardData: Vector[KinesisRecord],
      stream: StreamData
  ): StreamData = {
    val now = Utils.now
    val newStartingHashKeyNumber = BigInt(newStartingHashKey)
    val newShardIndex1 =
      stream.shards.keys.map(_.shardId.index).max + 1
    val newShardIndex2 = newShardIndex1 + 1
    val newShard1: (Shard, Vector[KinesisRecord]) = Shard(
      None,
      None,
      now,
      HashKeyRange(
        startingHashKey = shard.hashKeyRange.startingHashKey,
        endingHashKey = newStartingHashKeyNumber - BigInt(1)
      ),
      Some(shard.shardId.shardId),
      SequenceNumberRange(
        None,
        SequenceNumber.create(now, newShardIndex1, None, None, None)
      ),
      ShardId.create(newShardIndex1)
    ) -> Vector.empty

    val newShard2: (Shard, Vector[KinesisRecord]) = Shard(
      None,
      None,
      now,
      HashKeyRange(
        startingHashKey = newStartingHashKeyNumber,
        endingHashKey = shard.hashKeyRange.endingHashKey
      ),
      Some(shard.shardId.shardId),
      SequenceNumberRange(
        None,
        SequenceNumber.create(now, newShardIndex2, None, None, None)
      ),
      ShardId.create(newShardIndex2)
    ) -> Vector.empty

    val newShards = Vector(newShard1, newShard2)

    val oldShard: (Shard, Vector[KinesisRecord]) = shard.copy(
      closedTimestamp = Some(now),
      sequenceNumberRange = shard.sequenceNumberRange.copy(
        endingSequenceNumber = Some(SequenceNumber.shardEnd)
      )
    ) -> shardData

    stream.copy(
      shards = stream.shards.filterNot { case (shard, _) =>
        shard.shardId == oldShard._1.shardId
      } ++ (newShards :+ oldShard),
      streamStatus = StreamStatus.UPDATING
    )
  }

  implicit val splitShardRequestCirceEncoder: circe.Encoder[SplitShardRequest] =
    circe.Encoder.forProduct4(
      "NewStartingHashKey",
      "ShardToSplit",
      "StreamName",
      "StreamARN"
    )(x => (x.newStartingHashKey, x.shardToSplit, x.streamName, x.streamArn))

  implicit val splitShardRequestCirceDecoder: circe.Decoder[SplitShardRequest] =
    x =>
      for {
        newStartingHashKey <- x.downField("NewStartingHashKey").as[String]
        shardToSplit <- x.downField("ShardToSplit").as[String]
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      } yield SplitShardRequest(
        newStartingHashKey,
        shardToSplit,
        streamName,
        streamArn
      )

  implicit val splitShardRequestEncoder: Encoder[SplitShardRequest] =
    Encoder.derive
  implicit val splitShardRequestDecoder: Decoder[SplitShardRequest] =
    Decoder.derive

  implicit val splitShardRequestEq: Eq[SplitShardRequest] =
    Eq.fromUniversalEquals
}
