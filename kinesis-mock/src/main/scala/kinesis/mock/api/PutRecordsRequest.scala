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

import scala.collection.mutable.HashMap

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class PutRecordsRequest(
    records: Vector[PutRecordsRequestEntry],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def putRecords(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[PutRecordsResponse]] =
    Utils.now.flatMap { now =>
      streamsRef.modify[Response[PutRecordsResponse]] { streams =>
        CommonValidations
          .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
          .flatMap { case (name, arn) =>
            CommonValidations
              .validateStreamName(name)
              .flatMap(_ =>
                CommonValidations
                  .findStream(arn, streams)
                  .flatMap { stream =>
                    (
                      CommonValidations
                        .isStreamActiveOrUpdating(arn, streams),
                      records.traverse(x =>
                        (
                          CommonValidations
                            .validatePartitionKey(x.partitionKey),
                          x.explicitHashKey match {
                            case Some(explHashKey) =>
                              CommonValidations
                                .validateExplicitHashKey(explHashKey)
                            case None => Right(())
                          },
                          CommonValidations.validateData(x.data),
                          CommonValidations
                            .computeShard(
                              x.partitionKey,
                              x.explicitHashKey,
                              stream
                            )
                            .flatMap { case (shard, records) =>
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
          }
          .map { case (stream, recs) =>
            val asRecords = PutRecordsRequest
              .getIndexByShard(recs)
              .map { case (shard, records, entry, index) =>
                val seqNo = SequenceNumber.create(
                  shard.createdAtTimestamp,
                  shard.shardId.index,
                  None,
                  if (records.isEmpty) Some(1 + index)
                  else Some(1 + records.length + index),
                  Some(now)
                )

                (
                  shard,
                  records,
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

            val newShards = asRecords
              .groupBy { case (shard, currentRecords, _, _) =>
                (shard, currentRecords)
              }
              .map { case ((shard, currentRecords), recordsToAdd) =>
                (
                  shard,
                  currentRecords ++ recordsToAdd.map(_._3)
                )
              }

            (
              streams.updateStream(
                stream.copy(
                  shards = stream.shards ++ newShards
                )
              ),
              PutRecordsResponse(
                stream.encryptionType,
                0,
                asRecords.map { case (_, _, _, entry) =>
                  entry
                }
              )
            )
          }
          .sequenceWithDefault(streams)
      }
    }
}

object PutRecordsRequest {
  private def getIndexByShard(
      records: Vector[(Shard, Vector[KinesisRecord], PutRecordsRequestEntry)]
  ): Vector[(Shard, Vector[KinesisRecord], PutRecordsRequestEntry, Int)] = {
    val indexMap: HashMap[Shard, Int] = new HashMap()

    records.map { case (shard, records, entry) =>
      val i = indexMap.get(shard).fold(0)(_ + 1)
      indexMap += shard -> i
      (shard, records, entry, i)
    }
  }

  implicit val putRecordsRequestCirceEncoder: circe.Encoder[PutRecordsRequest] =
    circe.Encoder.forProduct3("Records", "StreamName", "StreamARN")(x =>
      (x.records, x.streamName, x.streamArn)
    )

  implicit val putRecordsRequestCirceDecoder: circe.Decoder[PutRecordsRequest] =
    x =>
      for {
        records <- x.downField("Records").as[Vector[PutRecordsRequestEntry]]
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      } yield PutRecordsRequest(records, streamName, streamArn)

  implicit val putRecordsRequestEncoder: Encoder[PutRecordsRequest] =
    Encoder.derive
  implicit val putRecordsRequestDecoder: Decoder[PutRecordsRequest] =
    Decoder.derive

  implicit val putRecordsRequestEq: Eq[PutRecordsRequest] = (x, y) =>
    x.records === y.records &&
      x.streamName == y.streamName &&
      x.streamArn == y.streamArn
}
