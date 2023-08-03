/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package api

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class GetShardIteratorRequest(
    shardId: String,
    shardIteratorType: ShardIteratorType,
    startingSequenceNumber: Option[SequenceNumber],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    timestamp: Option[Instant]
) {
  def getShardIterator(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[GetShardIteratorResponse]] = Utils.now.flatMap { now =>
    streamsRef.get.map { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
                .flatMap(stream =>
                  (
                    CommonValidations
                      .isStreamActiveOrUpdating(arn, streams),
                    startingSequenceNumber match {
                      case Some(sequenceNumber) =>
                        CommonValidations.validateSequenceNumber(sequenceNumber)
                      case None => Right(())
                    },
                    (
                      shardIteratorType,
                      startingSequenceNumber,
                      timestamp
                    ) match {
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
                                name,
                                shardId,
                                shard.sequenceNumberRange.startingSequenceNumber,
                                now
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
                                    name,
                                    shardId,
                                    shard.sequenceNumberRange.startingSequenceNumber,
                                    now
                                  )
                                )
                              )
                            case (ShardIteratorType.LATEST, _, _) =>
                              Right(
                                GetShardIteratorResponse(
                                  ShardIterator.create(
                                    name,
                                    shardId,
                                    data.last.sequenceNumber,
                                    now
                                  )
                                )
                              )
                            case (
                                  ShardIteratorType.AT_TIMESTAMP,
                                  _,
                                  Some(ts)
                                ) =>
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
                                      name,
                                      shardId,
                                      sequenceNumber,
                                      now
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
                                    name,
                                    shardId,
                                    shard.sequenceNumberRange.startingSequenceNumber,
                                    now
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
                                          name,
                                          shardId,
                                          shard.sequenceNumberRange.startingSequenceNumber,
                                          now
                                        )
                                      )
                                    )
                                  else
                                    Right(
                                      GetShardIteratorResponse(
                                        ShardIterator.create(
                                          name,
                                          shardId,
                                          data(
                                            data.indexOf(record) - 1
                                          ).sequenceNumber,
                                          now
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
                                    name,
                                    shardId,
                                    shard.sequenceNumberRange.startingSequenceNumber,
                                    now
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
                                        name,
                                        shardId,
                                        data(
                                          data.indexOf(record)
                                        ).sequenceNumber,
                                        now
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
  }
}

object GetShardIteratorRequest {
  def getShardIteratorRequestCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[GetShardIteratorRequest] =
    circe.Encoder.forProduct6(
      "ShardId",
      "ShardIteratorType",
      "StartingSequenceNumber",
      "StreamName",
      "StreamARN",
      "Timestamp"
    )(x =>
      (
        x.shardId,
        x.shardIteratorType,
        x.startingSequenceNumber,
        x.streamName,
        x.streamArn,
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
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
        timestamp <- x.downField("Timestamp").as[Option[Instant]]
      } yield GetShardIteratorRequest(
        shardId,
        shardIteratorType,
        startingSequenceNumber,
        streamName,
        streamArn,
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
        x.streamArn == y.streamArn &&
        x.timestamp.map(_.getEpochSecond()) == y.timestamp.map(
          _.getEpochSecond()
        )
}
