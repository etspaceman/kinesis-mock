package kinesis.mock
package api

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_MergeShards.html
final case class MergeShardsRequest(
    adjacentShardToMerge: String,
    shardToMerge: String,
    streamName: StreamName
) {
  def mergeShards(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] =
    streamsRef.modify(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap { stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                CommonValidations.validateShardId(shardToMerge),
                CommonValidations.validateShardId(adjacentShardToMerge),
                CommonValidations
                  .findShard(adjacentShardToMerge, stream)
                  .flatMap { case (adjacentShard, adjacentData) =>
                    CommonValidations.isShardOpen(adjacentShard).flatMap { _ =>
                      CommonValidations
                        .findShard(shardToMerge, stream)
                        .flatMap { case (shard, shardData) =>
                          CommonValidations.isShardOpen(shard).flatMap { _ =>
                            if (
                              adjacentShard.hashKeyRange
                                .isAdjacent(shard.hashKeyRange)
                            )
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
                  (stream, (adjacentShard, adjacentData), (shard, shardData))
              }
            }
        )
        .map {
          case (
                stream,
                (adjacentShard, adjacentData),
                (shard, shardData)
              ) =>
            val now = Instant.now()
            val newShardIndex =
              stream.shards.keys.map(_.shardId.index).max + 1
            val newShard: (Shard, Vector[KinesisRecord]) = Shard(
              Some(adjacentShard.shardId.shardId),
              None,
              now,
              HashKeyRange(
                Math.max(
                  adjacentShard.hashKeyRange.endingHashKey.toLong,
                  shard.hashKeyRange.endingHashKey.toLong
                ),
                Math.min(
                  adjacentShard.hashKeyRange.startingHashKey.toLong,
                  shard.hashKeyRange.startingHashKey.toLong
                )
              ),
              Some(shard.shardId.shardId),
              SequenceNumberRange(
                None,
                if (
                  adjacentShard.sequenceNumberRange.startingSequenceNumber.numericValue < shard.sequenceNumberRange.startingSequenceNumber.numericValue
                )
                  adjacentShard.sequenceNumberRange.startingSequenceNumber
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
            (
              streams.updateStream(
                stream.copy(
                  shards = stream.shards.filterNot { case (s, _) =>
                    s.shardId == adjacentShard.shardId || s.shardId == shard.shardId
                  }
                    ++ (oldShards :+ newShard),
                  streamStatus = StreamStatus.UPDATING
                )
              ),
              ()
            )
        }
        .sequenceWithDefault(streams)
    )
}

object MergeShardsRequest {
  implicit val mergeShardsRequestCirceEncoder
      : circe.Encoder[MergeShardsRequest] =
    circe.Encoder.forProduct3(
      "AdjacentShardToMerge",
      "ShardToMerge",
      "StreamName"
    )(x => (x.adjacentShardToMerge, x.shardToMerge, x.streamName))

  implicit val mergeShardsRequestCirceDecoder
      : circe.Decoder[MergeShardsRequest] =
    x =>
      for {
        adjacentShardToMerge <- x.downField("AdjacentShardToMerge").as[String]
        shardToMerge <- x.downField("ShardToMerge").as[String]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield MergeShardsRequest(adjacentShardToMerge, shardToMerge, streamName)
  implicit val mergeShardsRequestEncoder: Encoder[MergeShardsRequest] =
    Encoder.derive
  implicit val mergeShardsRequestDecoder: Decoder[MergeShardsRequest] =
    Decoder.derive
  implicit val mergeShardsRequestEq: Eq[MergeShardsRequest] =
    Eq.fromUniversalEquals
}
