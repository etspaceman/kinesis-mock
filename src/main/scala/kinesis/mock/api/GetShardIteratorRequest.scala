package kinesis.mock
package api

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class GetShardIteratorRequest(
    shardId: String,
    shardIteratorType: ShardIteratorType,
    startingSequenceNumber: Option[SequenceNumber],
    streamName: StreamName,
    timestamp: Option[Instant]
) {
  def getShardIterator(
      streams: Streams
  ): ValidatedNel[KinesisMockException, GetShardIteratorResponse] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.isStreamActiveOrUpdating(streamName, streams),
          startingSequenceNumber match {
            case Some(sequenceNumber) =>
              CommonValidations.validateSequenceNumber(sequenceNumber)
            case None => Valid(())
          },
          (shardIteratorType, startingSequenceNumber, timestamp) match {
            case (ShardIteratorType.AT_SEQUENCE_NUMBER, None, _) |
                (ShardIteratorType.AFTER_SEQUENCE_NUMBER, None, _) =>
              InvalidArgumentException(
                s"StartingSequenceNumber must be provided for ShardIteratorType $shardIteratorType"
              ).invalidNel
            case (ShardIteratorType.AT_TIMESTAMP, _, None) =>
              InvalidArgumentException(
                s"Timestamp must be provided for ShardIteratorType $shardIteratorType"
              ).invalidNel
            case _ => Valid(())
          },
          CommonValidations.validateShardId(shardId),
          CommonValidations.findShard(shardId, stream).andThen {
            case (shard, data) =>
              if (data.isEmpty)
                Valid(
                  GetShardIteratorResponse(
                    ShardIterator.create(
                      streamName,
                      shardId,
                      shard.sequenceNumberRange.startingSequenceNumber
                    )
                  )
                )
              else
                (shardIteratorType, startingSequenceNumber, timestamp) match {
                  case (ShardIteratorType.TRIM_HORIZON, _, _) =>
                    Valid(
                      GetShardIteratorResponse(
                        ShardIterator.create(
                          streamName,
                          shardId,
                          shard.sequenceNumberRange.startingSequenceNumber
                        )
                      )
                    )
                  case (ShardIteratorType.LATEST, _, _) =>
                    Valid(
                      GetShardIteratorResponse(
                        ShardIterator.create(
                          streamName,
                          shardId,
                          data.last.sequenceNumber
                        )
                      )
                    )
                  case (ShardIteratorType.AT_TIMESTAMP, _, Some(ts)) =>
                    val now = Instant.now()
                    if (ts.toEpochMilli > now.toEpochMilli)
                      InvalidArgumentException(
                        s"Timestamp cannot be in the future"
                      ).invalidNel
                    else
                      Valid(
                        GetShardIteratorResponse(
                          ShardIterator.create(
                            streamName,
                            shardId,
                            data
                              .findLast(
                                _.approximateArrivalTimestamp.toEpochMilli < ts.toEpochMilli
                              )
                              .map(_.sequenceNumber)
                              .getOrElse(data.last.sequenceNumber)
                          )
                        )
                      )
                  case (ShardIteratorType.AT_SEQUENCE_NUMBER, Some(seqNo), _) =>
                    data.find(_.sequenceNumber == seqNo) match {
                      case Some(record) =>
                        if (record == data.head)
                          Valid(
                            GetShardIteratorResponse(
                              ShardIterator.create(
                                streamName,
                                shardId,
                                shard.sequenceNumberRange.startingSequenceNumber
                              )
                            )
                          )
                        else
                          Valid(
                            GetShardIteratorResponse(
                              ShardIterator.create(
                                streamName,
                                shardId,
                                data(data.indexOf(record) - 1).sequenceNumber
                              )
                            )
                          )
                      case None =>
                        ResourceNotFoundException(
                          s"Unable to find record with provided SequenceNumber $seqNo in stream $streamName"
                        ).invalidNel
                    }

                  case (
                        ShardIteratorType.AFTER_SEQUENCE_NUMBER,
                        Some(seqNo),
                        _
                      ) =>
                    data.find(_.sequenceNumber == seqNo) match {
                      case Some(record) =>
                        Valid(
                          GetShardIteratorResponse(
                            ShardIterator.create(
                              streamName,
                              shardId,
                              data(data.indexOf(record)).sequenceNumber
                            )
                          )
                        )
                      case None =>
                        ResourceNotFoundException(
                          s"Unable to find record with provided SequenceNumber $seqNo in stream $streamName"
                        ).invalidNel
                    }

                  case _ =>
                    InvalidArgumentException(
                      s"Request for GetShardIterator invalid. ShardIteratorType: $shardIteratorType, StartingSequenceNumber: $startingSequenceNumber, Timestamp: $timestamp"
                    ).invalidNel
                }
          }
        ).mapN((_, _, _, _, _, res) => res)
      )
}

object GetShardIteratorRequest {
  implicit val getShardIteratorRequestCirceEncoder
      : Encoder[GetShardIteratorRequest] =
    Encoder.forProduct5(
      "ShardId",
      "ShardIteratorType",
      "StartingSequenceNumber",
      "StreamName",
      "Timestamp"
    )(x =>
      (
        x.shardId,
        x.shardIteratorType,
        x.startingSequenceNumber,
        x.streamName,
        x.timestamp
      )
    )

  implicit val getShardIteratorRequestCirceDecoder
      : Decoder[GetShardIteratorRequest] =
    x =>
      for {
        shardId <- x.downField("ShardId").as[String]
        shardIteratorType <- x
          .downField("ShardIteratorType")
          .as[ShardIteratorType]
        startingSequenceNumber <- x
          .downField("StartingSequenceNumber")
          .as[Option[SequenceNumber]]
        streamName <- x.downField("StreamName").as[StreamName]
        timestamp <- x.downField("Timestamp").as[Option[Instant]]
      } yield GetShardIteratorRequest(
        shardId,
        shardIteratorType,
        startingSequenceNumber,
        streamName,
        timestamp
      )

  implicit val getShardIteratorRequestEq: Eq[GetShardIteratorRequest] =
    (x, y) =>
      x.shardId == y.shardId &&
        x.shardIteratorType == y.shardIteratorType &&
        x.startingSequenceNumber == y.startingSequenceNumber &&
        x.streamName == y.streamName &&
        x.timestamp.map(_.getEpochSecond()) == y.timestamp.map(
          _.getEpochSecond()
        )
}
