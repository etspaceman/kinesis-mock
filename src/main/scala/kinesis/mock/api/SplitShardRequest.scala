package kinesis.mock
package api

import java.time.Instant

import cats.Eq
import cats.effect.{Concurrent, IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SplitShard.html
final case class SplitShardRequest(
    newStartingHashKey: String,
    shardToSplit: String,
    streamName: StreamName
) {
  def splitShard(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int
  )(implicit C: Concurrent[IO]): IO[Response[Unit]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap { stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                CommonValidations.validateShardId(shardToSplit),
                if (!newStartingHashKey.matches("0|([1-9]\\d{0,38})")) {
                  InvalidArgumentException(
                    "NewStartingHashKey contains invalid characters"
                  ).asLeft
                } else Right(newStartingHashKey),
                if (
                  streams.streams.values.map(_.shards.size).sum + 1 > shardLimit
                )
                  LimitExceededException(
                    "Operation would exceed the configured shard limit for the account"
                  ).asLeft
                else Right(()),
                CommonValidations.findShard(shardToSplit, stream).flatMap {
                  case (shard, shardData) =>
                    CommonValidations.isShardOpen(shard).flatMap { _ =>
                      val newStartingHashKeyNumber = BigInt(newStartingHashKey)
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
        .traverse { case (shard, shardData, stream) =>
          val now = Instant.now()
          val newStartingHashKeyNumber = BigInt(newStartingHashKey)
          val newShardIndex1 = stream.shards.keys.map(_.shardId.index).max + 1
          val newShardIndex2 = newShardIndex1 + 1
          val newShard1: (Shard, Vector[KinesisRecord]) = Shard(
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
          ) -> Vector.empty

          val newShard2: (Shard, Vector[KinesisRecord]) = Shard(
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
          ) -> Vector.empty

          val newShards = Vector(newShard1, newShard2)

          val oldShard: (Shard, Vector[KinesisRecord]) = shard.copy(
            closedTimestamp = Some(now),
            sequenceNumberRange = shard.sequenceNumberRange.copy(
              endingSequenceNumber = Some(SequenceNumber.shardEnd)
            )
          ) -> shardData

          streamsRef.update(x =>
            x.updateStream(
              stream.copy(
                shards = stream.shards.filterNot { case (shard, _) =>
                  shard.shardId == oldShard._1.shardId
                } ++ (newShards :+ oldShard),
                streamStatus = StreamStatus.UPDATING
              )
            )
          )
        }
    }
}

object SplitShardRequest {
  implicit val splitShardRequestCirceEncoder: circe.Encoder[SplitShardRequest] =
    circe.Encoder.forProduct3(
      "NewStartingHashKey",
      "ShardToSplit",
      "StreamName"
    )(x => (x.newStartingHashKey, x.shardToSplit, x.streamName))

  implicit val splitShardRequestCirceDecoder: circe.Decoder[SplitShardRequest] =
    x =>
      for {
        newStartingHashKey <- x.downField("NewStartingHashKey").as[String]
        shardToSplit <- x.downField("ShardToSplit").as[String]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield SplitShardRequest(newStartingHashKey, shardToSplit, streamName)

  implicit val splitShardRequestEncoder: Encoder[SplitShardRequest] =
    Encoder.derive
  implicit val splitShardRequestDecoder: Decoder[SplitShardRequest] =
    Decoder.derive

  implicit val splitShardRequestEq: Eq[SplitShardRequest] =
    Eq.fromUniversalEquals
}
