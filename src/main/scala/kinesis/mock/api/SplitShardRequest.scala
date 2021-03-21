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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SplitShard.html
final case class SplitShardRequest(
    newStartingHashKey: String,
    shardToSplit: String,
    streamName: StreamName
) {
  def splitShard(
      streams: Streams,
      shardSemaphores: Map[ShardSemaphoresKey, Semaphore[IO]],
      shardLimit: Int
  ): IO[
    ValidatedNel[KinesisMockException, (Streams, List[ShardSemaphoresKey])]
  ] = CommonValidations
    .findStream(streamName, streams)
    .andThen { stream =>
      (
        CommonValidations.validateStreamName(streamName),
        CommonValidations.isStreamActive(streamName, streams),
        CommonValidations.validateShardId(shardToSplit),
        if (!newStartingHashKey.matches("0|([1-9]\\d{0,38})")) {
          InvalidArgumentException(
            "NewStartingHashKey contains invalid characters"
          ).invalidNel
        } else Valid(newStartingHashKey),
        if (streams.streams.values.map(_.shards.size).sum + 1 > shardLimit)
          LimitExceededException(
            "Operation would exceed the configured shard limit for the account"
          ).invalidNel
        else Valid(()),
        CommonValidations.findShard(shardToSplit, stream).andThen {
          case (shard, shardData) =>
            CommonValidations.isShardOpen(shard).andThen { _ =>
              val newStartingHashKeyNumber = BigInt(newStartingHashKey)
              if (
                newStartingHashKeyNumber >= shard.hashKeyRange.startingHashKey && newStartingHashKeyNumber <= shard.hashKeyRange.endingHashKey
              )
                Valid((shard, shardData))
              else
                InvalidArgumentException(
                  s"NewStartingHashKey is not within the hash range shard ${shard.shardId}"
                ).invalidNel
            }
        }
      ).mapN { case (_, _, _, _, _, (shard, shardData)) =>
        (shard, shardData, stream)
      }
    }
    .traverse { case (shard, shardData, stream) =>
      val now = Instant.now()
      val newStartingHashKeyNumber = BigInt(newStartingHashKey)
      val newShardIndex1 = stream.shards.keys.map(_.shardId.index).max + 1
      val newShardIndex2 = newShardIndex1 + 1
      val newShard1: (Shard, List[KinesisRecord]) = Shard(
        None,
        None,
        now,
        HashKeyRange(
          shard.hashKeyRange.startingHashKey,
          newStartingHashKeyNumber - BigInt(1)
        ),
        Some(shard.shardId.shardId),
        SequenceNumberRange(
          None,
          SequenceNumber.create(now, newShardIndex1, None, None, None)
        ),
        ShardId.create(newShardIndex1)
      ) -> List.empty

      val newShard2: (Shard, List[KinesisRecord]) = Shard(
        None,
        None,
        now,
        HashKeyRange(
          newStartingHashKeyNumber,
          shard.hashKeyRange.endingHashKey
        ),
        Some(shard.shardId.shardId),
        SequenceNumberRange(
          None,
          SequenceNumber.create(now, newShardIndex2, None, None, None)
        ),
        ShardId.create(newShardIndex2)
      ) -> List.empty

      val newShards = List(newShard1, newShard2)

      val oldShard: (Shard, List[KinesisRecord]) = shard.copy(
        closedTimestamp = Some(now),
        sequenceNumberRange = shard.sequenceNumberRange.copy(
          endingSequenceNumber = Some(SequenceNumber.shardEnd)
        )
      ) -> shardData

      shardSemaphores(ShardSemaphoresKey(streamName, shard)).acquire.map(_ =>
        (
          streams.updateStream(
            stream.copy(
              shards = stream.shards ++ (newShards :+ oldShard),
              streamStatus = StreamStatus.UPDATING
            )
          ),
          List(
            ShardSemaphoresKey(streamName, newShard1._1),
            ShardSemaphoresKey(streamName, newShard2._1)
          )
        )
      )
    }
}

object SplitShardRequest {
  implicit val splitShardRequestCirceEncoder: Encoder[SplitShardRequest] =
    Encoder.forProduct3("NewStartingHashKey", "ShardToSplit", "StreamName")(x =>
      (x.newStartingHashKey, x.shardToSplit, x.streamName)
    )

  implicit val splitShardRequestCirceDecoder: Decoder[SplitShardRequest] =
    x =>
      for {
        newStartingHashKey <- x.downField("NewStartingHashKey").as[String]
        shardToSplit <- x.downField("ShardToSplit").as[String]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield SplitShardRequest(newStartingHashKey, shardToSplit, streamName)

  implicit val splitShardRequestEq: Eq[SplitShardRequest] =
    Eq.fromUniversalEquals
}
