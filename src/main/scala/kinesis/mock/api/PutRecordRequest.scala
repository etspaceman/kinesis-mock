package kinesis.mock
package api

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.syntax.either._
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
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[PutRecordResponse]] = streamsRef.modify { streams =>
    val streamArn = StreamArn(awsRegion, streamName, awsAccountId)
    val now = Instant.now()
    CommonValidations
      .validateStreamName(streamName)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamArn, streams)
          .flatMap { stream =>
            (
              CommonValidations.isStreamActiveOrUpdating(streamArn, streams),
              CommonValidations.validateData(data),
              sequenceNumberForOrdering match {
                case None => Right(())
                case Some(seqNo) =>
                  seqNo.parse.flatMap {
                    case parts: SequenceNumberParts
                        if parts.seqTime.toEpochMilli > now.toEpochMilli =>
                      InvalidArgumentException(
                        s"Sequence time in the future"
                      ).asLeft
                    case x => Right(x)
                  }
              },
              CommonValidations.validatePartitionKey(partitionKey),
              explicitHashKey match {
                case Some(explHashKeh) =>
                  CommonValidations.validateExplicitHashKey(explHashKeh)
                case None => Right(())
              },
              CommonValidations
                .computeShard(partitionKey, explicitHashKey, stream)
                .flatMap { case (shard, records) =>
                  CommonValidations
                    .isShardOpen(shard)
                    .map(_ => (shard, records))

                }
            ).mapN { case (_, _, _, _, _, (shard, records)) =>
              (stream, shard, records)
            }
          }
      )
      .map { case (stream, shard, records) =>
        val seqNo = SequenceNumber.create(
          shard.createdAtTimestamp,
          shard.shardId.index,
          None,
          Some(records.length),
          Some(now)
        )
        (
          streams.updateStream {
            stream.copy(
              shards = stream.shards ++ Map(
                shard -> (records :+ KinesisRecord(
                  now,
                  data,
                  stream.encryptionType,
                  partitionKey,
                  seqNo
                ))
              )
            )
          },
          PutRecordResponse(
            stream.encryptionType,
            seqNo,
            shard.shardId.shardId
          )
        )
      }
      .sequenceWithDefault(streams)
  }
}

object PutRecordRequest {
  implicit val purtRecordRequestCirceEncoder: circe.Encoder[PutRecordRequest] =
    circe.Encoder.forProduct5(
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

  implicit val putRecordRequestCirceDecoder: circe.Decoder[PutRecordRequest] =
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

  implicit val putRecordRequestEncoder: Encoder[PutRecordRequest] =
    Encoder.derive
  implicit val putRecordRequestDecoder: Decoder[PutRecordRequest] =
    Decoder.derive

  implicit val putRecordRequestEq: Eq[PutRecordRequest] = (x, y) =>
    x.data.sameElements(y.data) &&
      x.explicitHashKey == y.explicitHashKey &&
      x.partitionKey == y.partitionKey &&
      x.sequenceNumberForOrdering == y.sequenceNumberForOrdering &&
      x.streamName == y.streamName
}
