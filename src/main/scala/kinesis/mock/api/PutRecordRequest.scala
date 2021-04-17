package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.{Ref, Semaphore}
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class PutRecordRequest(
    data: Array[Byte],
    explicitHashKey: Option[String],
    partitionKey: String,
    sequenceNumberForOrdering: Option[SequenceNumber],
    streamName: StreamName
) {
  def putRecord(
      streamsRef: Ref[IO, Streams],
      shardSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]]
  ): IO[ValidatedResponse[PutRecordResponse]] = streamsRef.get.flatMap {
    streams =>
      val now = Instant.now()
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen { stream =>
              (
                CommonValidations.isStreamActiveOrUpdating(streamName, streams),
                CommonValidations.validateData(data),
                sequenceNumberForOrdering match {
                  case None => Valid(())
                  case Some(seqNo) =>
                    seqNo.parse.andThen {
                      case parts: SequenceNumberParts
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
                CommonValidations
                  .computeShard(partitionKey, explicitHashKey, stream)
                  .andThen { case (shard, records) =>
                    CommonValidations
                      .isShardOpen(shard)
                      .map(_ => (shard, records))

                  }
              ).mapN { case (_, _, _, _, _, (shard, records)) =>
                (stream, shard, records)
              }
            }
        )
        .traverse { case (stream, shard, records) =>
          val seqNo = SequenceNumber.create(
            shard.createdAtTimestamp,
            shard.shardId.index,
            None,
            Some(records.length),
            Some(now)
          )
          // Use a semaphore to ensure synchronous operations on the shard
          shardSemaphoresRef.get.flatMap(shardSemaphores =>
            shardSemaphores(ShardSemaphoresKey(streamName, shard)).withPermit(
              streamsRef
                .update(x =>
                  x.updateStream {
                    stream.copy(
                      shards = stream.shards ++ SortedMap(
                        shard -> (records :+ KinesisRecord(
                          now,
                          data,
                          stream.encryptionType,
                          partitionKey,
                          seqNo
                        ))
                      )
                    )
                  }
                )
                .as(
                  PutRecordResponse(
                    stream.encryptionType,
                    seqNo,
                    shard.shardId.shardId
                  )
                )
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
        streamName <- x.downField("StreamName").as[StreamName]
      } yield PutRecordRequest(
        data,
        explicitHashKey,
        partitionKey,
        sequenceNumberForOrdering,
        streamName
      )

  implicit val putRecordRequestEq: Eq[PutRecordRequest] = (x, y) =>
    x.data.sameElements(y.data) &&
      x.explicitHashKey == y.explicitHashKey &&
      x.partitionKey == y.partitionKey &&
      x.sequenceNumberForOrdering == y.sequenceNumberForOrdering &&
      x.streamName == y.streamName
}
