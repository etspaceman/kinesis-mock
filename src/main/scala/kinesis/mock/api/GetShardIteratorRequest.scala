package kinesis.mock
package api

import java.time.Instant

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

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
      streamsRef: Ref[IO, Streams]
  ): IO[Response[GetShardIteratorResponse]] = streamsRef.get.map { streams =>
    CommonValidations
      .validateStreamName(streamName)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .flatMap(stream =>
            (
              CommonValidations.isStreamActiveOrUpdating(streamName, streams),
              startingSequenceNumber match {
                case Some(sequenceNumber) =>
                  CommonValidations.validateSequenceNumber(sequenceNumber)
                case None => Right(())
              },
              (shardIteratorType, startingSequenceNumber, timestamp) match {
                case (ShardIteratorType.AT_SEQUENCE_NUMBER, None, _) |
                    (ShardIteratorType.AFTER_SEQUENCE_NUMBER, None, _) =>
                  InvalidArgumentException(
                    s"StartingSequenceNumber must be provided for ShardIteratorType $shardIteratorType"
                  ).asLeft
                case (ShardIteratorType.AT_TIMESTAMP, _, None) =>
                  InvalidArgumentException(
                    s"Timestamp must be provided for ShardIteratorType $shardIteratorType"
                  ).asLeft
                case _ => Right(())
              },
              CommonValidations.validateShardId(shardId),
              CommonValidations.findShard(shardId, stream).flatMap {
                case (shard, data) =>
                  if (data.isEmpty)
                    Right(
                      GetShardIteratorResponse(
                        ShardIterator.create(
                          streamName,
                          shardId,
                          shard.sequenceNumberRange.startingSequenceNumber
                        )
                      )
                    )
                  else
                    (
                      shardIteratorType,
                      startingSequenceNumber,
                      timestamp
                    ) match {
                      case (ShardIteratorType.TRIM_HORIZON, _, _) =>
                        Right(
                          GetShardIteratorResponse(
                            ShardIterator.create(
                              streamName,
                              shardId,
                              shard.sequenceNumberRange.startingSequenceNumber
                            )
                          )
                        )
                      case (ShardIteratorType.LATEST, _, _) =>
                        Right(
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
                          ).asLeft
                        else {
                          val sequenceNumber =
                            data
                              .find(
                                _.approximateArrivalTimestamp.toEpochMilli >= ts.toEpochMilli
                              )
                              .map(data.indexOf)
                              .flatMap(x =>
                                if (x == 0)
                                  Some(
                                    shard.sequenceNumberRange.startingSequenceNumber
                                  )
                                else
                                  data
                                    .get(x.toLong - 1L)
                                    .map(_.sequenceNumber)
                              )
                              .getOrElse(data.last.sequenceNumber)
                          Right(
                            GetShardIteratorResponse(
                              ShardIterator.create(
                                streamName,
                                shardId,
                                sequenceNumber
                              )
                            )
                          )
                        }
                      case (
                            ShardIteratorType.AT_SEQUENCE_NUMBER,
                            Some(seqNo),
                            _
                          ) if seqNo == shard.sequenceNumberRange.startingSequenceNumber =>
                        Right(
                          GetShardIteratorResponse(
                            ShardIterator.create(
                              streamName,
                              shardId,
                              shard.sequenceNumberRange.startingSequenceNumber
                            )
                          )
                        )
                      case (
                            ShardIteratorType.AT_SEQUENCE_NUMBER,
                            Some(seqNo),
                            _
                          ) =>
                        data.find(_.sequenceNumber == seqNo) match {
                          case Some(record) =>
                            if (record == data.head)
                              Right(
                                GetShardIteratorResponse(
                                  ShardIterator.create(
                                    streamName,
                                    shardId,
                                    shard.sequenceNumberRange.startingSequenceNumber
                                  )
                                )
                              )
                            else
                              Right(
                                GetShardIteratorResponse(
                                  ShardIterator.create(
                                    streamName,
                                    shardId,
                                    data(
                                      data.indexOf(record) - 1
                                    ).sequenceNumber
                                  )
                                )
                              )
                          case None =>
                            ResourceNotFoundException(
                              s"Unable to find record with provided SequenceNumber $seqNo in stream $streamName"
                            ).asLeft
                        }
                      case (
                            ShardIteratorType.AFTER_SEQUENCE_NUMBER,
                            Some(seqNo),
                            _
                          ) if seqNo == shard.sequenceNumberRange.startingSequenceNumber =>
                        Right(
                          GetShardIteratorResponse(
                            ShardIterator.create(
                              streamName,
                              shardId,
                              shard.sequenceNumberRange.startingSequenceNumber
                            )
                          )
                        )
                      case (
                            ShardIteratorType.AFTER_SEQUENCE_NUMBER,
                            Some(seqNo),
                            _
                          ) =>
                        data.find(_.sequenceNumber == seqNo) match {
                          case Some(record) =>
                            Right(
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
                            ).asLeft
                        }

                      case _ =>
                        InvalidArgumentException(
                          s"Request for GetShardIterator invalid. ShardIteratorType: $shardIteratorType, StartingSequenceNumber: $startingSequenceNumber, Timestamp: $timestamp"
                        ).asLeft
                    }
              }
            ).mapN((_, _, _, _, res) => res)
          )
      )
  }
}

object GetShardIteratorRequest {
  def getShardIteratorRequestCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[GetShardIteratorRequest] =
    circe.Encoder.forProduct5(
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

  def getShardIteratorRequestCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[GetShardIteratorRequest] =
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

  implicit val getShardIteratorRequestEncoder
      : Encoder[GetShardIteratorRequest] = Encoder.instance(
    getShardIteratorRequestCirceEncoder(instantBigDecimalCirceEncoder),
    getShardIteratorRequestCirceEncoder(instantLongCirceEncoder)
  )

  implicit val getShardIteratorRequestDecoder
      : Decoder[GetShardIteratorRequest] = Decoder.instance(
    getShardIteratorRequestCirceDecoder(instantBigDecimalCirceDecoder),
    getShardIteratorRequestCirceDecoder(instantLongCirceDecoder)
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
