package kinesis.mock
package api

import java.time.Instant

import cats.Parallel
import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.{Ref, Semaphore}
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class PutRecordsRequest(
    records: List[PutRecordsRequestEntry],
    streamName: StreamName
) {
  def putRecords(
      streamsRef: Ref[IO, Streams],
      shardSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]]
  )(implicit
      P: Parallel[IO]
  ): IO[ValidatedResponse[PutRecordsResponse]] =
    streamsRef.get.flatMap { streams =>
      val now = Instant.now()
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen { stream =>
              (
                CommonValidations.isStreamActiveOrUpdating(streamName, streams),
                records.traverse(x =>
                  (
                    CommonValidations.validatePartitionKey(x.partitionKey),
                    x.explicitHashKey match {
                      case Some(explHashKey) =>
                        CommonValidations.validateExplicitHashKey(explHashKey)
                      case None => Valid(())
                    },
                    CommonValidations.validateData(x.data),
                    CommonValidations
                      .computeShard(x.partitionKey, x.explicitHashKey, stream)
                      .andThen { case (shard, records) =>
                        CommonValidations
                          .isShardOpen(shard)
                          .map(_ => (shard, records))
                      }
                  ).mapN { case (_, _, _, (shard, records)) =>
                    (shard, records, x)
                  }
                )
              ).mapN((_, recs) => (stream, recs))
            }
        )
        .traverse { case (stream, recs) =>
          val grouped = recs
            .groupBy { case (shard, records, _) =>
              (shard, records)
            }
            .map { case ((shard, records), list) =>
              (shard, records) ->
                list.map(_._3).zipWithIndex.map { case (entry, index) =>
                  val seqNo = SequenceNumber.create(
                    shard.createdAtTimestamp,
                    shard.shardId.index,
                    None,
                    Some(records.length + index),
                    Some(now)
                  )
                  (
                    KinesisRecord(
                      now,
                      entry.data,
                      stream.encryptionType,
                      entry.partitionKey,
                      seqNo
                    ),
                    PutRecordsResultEntry(
                      None,
                      None,
                      Some(seqNo),
                      Some(shard.shardId.shardId)
                    )
                  )
                }
            }
            .toList

          shardSemaphoresRef.get.flatMap { shardSemaphores =>
            val keys = grouped.map { case ((shard, _), _) =>
              ShardSemaphoresKey(streamName, shard)
            }

            val semaphores = shardSemaphores.toList.filter { case (key, _) =>
              keys.contains(key)
            }

            for {
              _ <- semaphores.parTraverse { case (_, semaphore) =>
                semaphore.acquire
              }
              newShards = grouped.map {
                case ((shard, currentRecords), recordsToAdd) =>
                  (
                    shard,
                    (
                      currentRecords ++ recordsToAdd.map(_._1),
                      recordsToAdd.map(_._2)
                    )
                  )
              }
              _ <- streamsRef.update(x =>
                x.updateStream(
                  stream.copy(
                    shards = stream.shards ++ newShards.map {
                      case (shard, (records, _)) => shard -> records
                    }
                  )
                )
              )
              _ <- semaphores.parTraverse { case (_, semaphore) =>
                semaphore.release
              }
            } yield PutRecordsResponse(
              stream.encryptionType,
              0,
              newShards.flatMap { case (_, (_, resultEntries)) =>
                resultEntries
              }
            )
          }
        }
    }
}

object PutRecordsRequest {
  implicit val putRecordsRequestCirceEncoder: circe.Encoder[PutRecordsRequest] =
    circe.Encoder.forProduct2("Records", "StreamName")(x =>
      (x.records, x.streamName)
    )

  implicit val putRecordsRequestCirceDecoder: circe.Decoder[PutRecordsRequest] =
    x =>
      for {
        records <- x.downField("Records").as[List[PutRecordsRequestEntry]]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield PutRecordsRequest(records, streamName)

  implicit val putRecordsRequestEncoder: Encoder[PutRecordsRequest] =
    Encoder.derive
  implicit val putRecordsRequestDecoder: Decoder[PutRecordsRequest] =
    Decoder.derive

  implicit val putRecordsRequestEq: Eq[PutRecordsRequest] = (x, y) =>
    x.records === y.records &&
      x.streamName == y.streamName
}
