package kinesis.mock
package api

import java.time.Instant
import java.util.Base64

import cats.Parallel
import cats.data.Validated._
import cats.data._
import cats.effect.IO
import cats.effect.concurrent.Semaphore
import cats.syntax.all._
import io.circe._

import kinesis.mock.models.{
  KinesisRecord,
  SequenceNumber,
  ShardSemaphoresKey,
  Streams
}

final case class PutRecordsRequest(
    records: List[PutRecordsRequestEntry],
    streamName: String
) {
  def putRecords(
      streams: Streams,
      shardSemaphores: Map[ShardSemaphoresKey, Semaphore[IO]]
  )(implicit
      P: Parallel[IO]
  ): IO[ValidatedNel[KinesisMockException, (Streams, PutRecordsResponse)]] = {
    val now = Instant.now()

    CommonValidations
      .findStream(streamName, streams)
      .andThen { stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.isStreamActive(streamName, streams),
          records.traverse(x =>
            (
              CommonValidations.validatePartitionKey(x.partitionKey),
              x.explicitHashKey match {
                case Some(explHashKey) =>
                  CommonValidations.validateExplicitHashKey(explHashKey)
                case None => Valid(())
              },
              CommonValidations
                .computeShard(x.partitionKey, x.explicitHashKey, stream)
                .andThen { case (shard, records) =>
                  CommonValidations
                    .isShardOpen(shard)
                    .map(_ => (shard, records))
                }
            ).mapN { case (_, _, (shard, records)) => (shard, records, x) }
          )
        ).mapN((_, _, recs) => (stream, recs))
      }
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
                  shard.shardIndex,
                  None,
                  Some((records.length - 1) + index),
                  Some(now)
                )
                (
                  KinesisRecord(
                    now,
                    Base64.getDecoder().decode(entry.data),
                    stream.encryptionType,
                    entry.partitionKey,
                    seqNo
                  ),
                  PutRecordsResultEntry(
                    None,
                    None,
                    Some(seqNo),
                    Some(shard.shardId)
                  )
                )
              }
          }
          .toList

        grouped
          .parTraverse { case ((shard, currentRecords), recordsToAdd) =>
            shardSemaphores(ShardSemaphoresKey(streamName, shard)).acquire.map(
              _ =>
                (
                  shard,
                  (
                    (currentRecords ++ recordsToAdd.map(_._1)),
                    recordsToAdd.map(_._2)
                  )
                )
            )
          }
          .map { newShards =>
            (
              streams.updateStream(
                stream.copy(
                  shards = stream.shards ++ newShards.map {
                    case (shard, (records, _)) => shard -> records
                  }
                )
              ),
              PutRecordsResponse(
                stream.encryptionType,
                0,
                newShards.flatMap { case (_, (_, resultEntries)) =>
                  resultEntries
                }
              )
            )
          }
      }
  }
}

object PutRecordsRequest {
  implicit val putRecordsRequestCirceEncoder: Encoder[PutRecordsRequest] =
    Encoder.forProduct2("Records", "StreamName")(x => (x.records, x.streamName))

  implicit val putRecordsRequestCirceDecoder: Decoder[PutRecordsRequest] = x =>
    for {
      records <- x.downField("Records").as[List[PutRecordsRequestEntry]]
      streamName <- x.downField("StreamName").as[String]
    } yield PutRecordsRequest(records, streamName)
}
