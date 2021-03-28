package kinesis.mock
package api

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.effect.IO
import cats.effect.concurrent.Semaphore
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_MergeShards.html
final case class MergeShardsRequest(
    adjacentShardToMerge: String,
    shardToMerge: String,
    streamName: StreamName
) {
  def mergeShards(
      streams: Streams,
      shardSemaphores: Map[ShardSemaphoresKey, Semaphore[IO]]
  ): IO[ValidatedNel[KinesisMockException, (Streams, ShardSemaphoresKey)]] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen { stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.isStreamActive(streamName, streams),
          CommonValidations.validateShardId(shardToMerge),
          CommonValidations.validateShardId(adjacentShardToMerge),
          CommonValidations.findShard(adjacentShardToMerge, stream).andThen {
            case (adjacentShard, adjacentData) =>
              CommonValidations.isShardOpen(adjacentShard).andThen { _ =>
                CommonValidations.findShard(shardToMerge, stream).andThen {
                  case (shard, shardData) =>
                    CommonValidations.isShardOpen(shard).andThen { _ =>
                      if (
                        adjacentShard.hashKeyRange
                          .isAdjacent(shard.hashKeyRange)
                      )
                        Valid(
                          ((adjacentShard, adjacentData), (shard, shardData))
                        )
                      else
                        InvalidArgumentException(
                          "Provided shards are not adjacent"
                        ).invalidNel
                    }
                }
              }
          }
        ).mapN {
          case (
                _,
                _,
                _,
                _,
                ((adjacentShard, adjacentData), (shard, shardData))
              ) =>
            (stream, (adjacentShard, adjacentData), (shard, shardData))
        }
      }
      .traverse {
        case (stream, (adjacentShard, adjacentData), (shard, shardData)) => {
          val now = Instant.now()
          val newShardIndex = stream.shards.keys.map(_.shardId.index).max + 1
          val newShard: (Shard, List[KinesisRecord]) = Shard(
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
          ) -> List.empty

          val oldShards: List[(Shard, List[KinesisRecord])] = List(
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

          shardSemaphores(
            ShardSemaphoresKey(streamName, adjacentShard)
          ).withPermit(
            shardSemaphores(ShardSemaphoresKey(streamName, shard))
              .withPermit(
                IO(
                  (
                    streams.updateStream(
                      stream.copy(
                        shards = stream.shards ++ (oldShards :+ newShard),
                        streamStatus = StreamStatus.UPDATING
                      )
                    ),
                    ShardSemaphoresKey(streamName, newShard._1)
                  )
                )
              )
          )
        }
      }
}

object MergeShardsRequest {
  implicit val mergeShardsRequestCirceEncoder: Encoder[MergeShardsRequest] =
    Encoder.forProduct3("AdjacentShardToMerge", "ShardToMerge", "StreamName")(
      x => (x.adjacentShardToMerge, x.shardToMerge, x.streamName)
    )

  implicit val mergeShardsRequestCirceDecoder: Decoder[MergeShardsRequest] =
    x =>
      for {
        adjacentShardToMerge <- x.downField("AdjacentShardToMerge").as[String]
        shardToMerge <- x.downField("ShardToMerge").as[String]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield MergeShardsRequest(adjacentShardToMerge, shardToMerge, streamName)

  implicit val mergeShardsRequestEq: Eq[MergeShardsRequest] =
    Eq.fromUniversalEquals
}
