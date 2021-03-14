package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant
import java.util.Base64

import cats.data.Validated._
import cats.data._
import cats.effect.IO
import cats.effect.concurrent.Semaphore
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class PutRecordRequest(
    data: Array[Byte],
    explicitHashKey: Option[String],
    partitionKey: String,
    sequenceNumberForOrdering: Option[SequenceNumber],
    streamName: String
) {
  def putRecord(
      streams: Streams,
      shardSemaphores: Map[ShardSemaphoresKey, Semaphore[IO]]
  ): IO[ValidatedNel[KinesisMockException, (Streams, PutRecordResponse)]] = {
    val now = Instant.now()
    CommonValidations
      .findStream(streamName, streams)
      .andThen { stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.isStreamActive(streamName, streams),
          sequenceNumberForOrdering match {
            case None => Valid(())
            case Some(seqNo) =>
              seqNo.parse.andThen {
                case Right(parts)
                    if parts.seqTime.toEpochMilli > now.toEpochMilli =>
                  InvalidArgumentException(
                    s"Sequence time in the future"
                  ).invalidNel
                case x => Valid(x)
              }
          },
          CommonValidations.validatePartitionKey(partitionKey),
          explicitHashKey match {
            case Some(explHashKeh) =>
              CommonValidations.validateExplicitHashKey(explHashKeh)
            case None => Valid(())
          },
          CommonValidations.computeShard(partitionKey, explicitHashKey, stream)
        ).mapN { case (_, _, _, _, _, (shard, records)) =>
          (stream, shard, records)
        }
      }
      .traverse { case (stream, shard, records) =>
        val seqNo = SequenceNumber.create(
          shard.createdAtTimestamp,
          shard.shardIndex,
          None,
          Some(records.length - 1),
          Some(now)
        )
        // Use a semaphore to ensure synchronous operations on the shard
        shardSemaphores(ShardSemaphoresKey(streamName, shard)).acquire.map(_ =>
          (
            streams.updateStream(
              stream.copy(
                shards = stream.shards ++ SortedMap(
                  shard -> (records :+ KinesisRecord(
                    now,
                    Base64.getDecoder().decode(data),
                    stream.encryptionType,
                    partitionKey,
                    seqNo
                  ))
                )
              )
            ),
            PutRecordResponse(stream.encryptionType, seqNo, shard.shardId)
          )
        )
      }
  }
}

object PutRecordRequest {
  implicit val purtRecordRequestCirceEncoder: Encoder[PutRecordRequest] =
    Encoder.forProduct5(
      "Data",
      "ExplicitHashKey",
      "PartitionKey",
      "SequenceNumberForOrdering",
      "StreamName"
    )(x =>
      (
        x.data,
        x.explicitHashKey,
        x.partitionKey,
        x.sequenceNumberForOrdering,
        x.streamName
      )
    )

  implicit val putRecordRequestCirceDecoder: Decoder[PutRecordRequest] =
    x =>
      for {
        data <- x.downField("Data").as[Array[Byte]]
        explicitHashKey <- x.downField("ExplicitHashKey").as[Option[String]]
        partitionKey <- x.downField("PartitionKey").as[String]
        sequenceNumberForOrdering <- x
          .downField("SequenceNumberForOrdering")
          .as[Option[SequenceNumber]]
        streamName <- x.downField("StreamName").as[String]
      } yield PutRecordRequest(
        data,
        explicitHashKey,
        partitionKey,
        sequenceNumberForOrdering,
        streamName
      )
}
