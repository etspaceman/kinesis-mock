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

import scala.annotation.tailrec
import scala.collection.immutable.Queue

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class GetRecordsRequest(
    limit: Option[Int],
    shardIterator: ShardIterator,
    streamArn: Option[StreamArn]
) {
  def getRecords(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[GetRecordsResponse]] = Utils.now.flatMap { now =>
    streamsRef.get.map { streams =>
      shardIterator.parse(now).flatMap { parts =>
        val arn = streamArn.getOrElse(
          StreamArn(awsRegion, parts.streamName, awsAccountId)
        )
        CommonValidations
          .isStreamActiveOrUpdating(arn, streams)
          .flatMap(_ =>
            CommonValidations
              .findStream(arn, streams)
              .flatMap(stream =>
                CommonValidations.findShard(parts.shardId, stream).flatMap {
                  case (shard, data) =>
                    (limit match {
                      case Some(l) => CommonValidations.validateLimit(l)
                      case None    => Right(())
                    }).flatMap { _ =>
                      val allShards = stream.shards.keys.toVector
                      val childShards = allShards
                        .filter(x =>
                          x.parentShardId.contains(shard.shardId.shardId) ||
                            x.adjacentParentShardId
                              .contains(shard.shardId.shardId)
                        )
                        .map(s =>
                          ChildShard.fromShard(
                            s,
                            allShards.filter(x =>
                              s.adjacentParentShardId.contains(
                                x.shardId.shardId
                              ) || s.parentShardId.contains(x.shardId.shardId)
                            )
                          )
                        )
                      if (data.isEmpty) {
                        Right(
                          GetRecordsResponse(
                            if (childShards.nonEmpty) Some(childShards)
                            else None,
                            0L,
                            if (childShards.nonEmpty) None
                            else
                              Some(
                                ShardIterator.create(
                                  parts.streamName,
                                  parts.shardId,
                                  parts.sequenceNumber,
                                  now
                                )
                              ),
                            Queue.empty
                          )
                        )
                      } else {
                        if (
                          parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber
                        ) {
                          val maxRecords = limit.getOrElse(10000)

                          val (head, records) = GetRecordsRequest
                            .getRecords(
                              data.take(maxRecords),
                              Queue.empty,
                              data.head,
                              0
                            )

                          val millisBehindLatest =
                            data.last.approximateArrivalTimestamp.toEpochMilli -
                              head.approximateArrivalTimestamp.toEpochMilli

                          Right(
                            GetRecordsResponse(
                              if (
                                records.length == data.length && childShards.nonEmpty
                              ) Some(childShards)
                              else None,
                              millisBehindLatest,
                              if (
                                records.length == data.length && childShards.nonEmpty
                              ) None
                              else
                                Some(
                                  ShardIterator.create(
                                    parts.streamName,
                                    parts.shardId,
                                    records.last.sequenceNumber,
                                    now
                                  )
                                ),
                              records
                            )
                          )
                        } else {
                          data
                            .indexWhere(
                              _.sequenceNumber == parts.sequenceNumber
                            ) match {
                            case -1 =>
                              ResourceNotFoundException(
                                s"Record for provided SequenceNumber not found"
                              ).asLeft
                            case index if index == data.length - 1 =>
                              Right(
                                GetRecordsResponse(
                                  if (childShards.nonEmpty) Some(childShards)
                                  else None,
                                  0L,
                                  if (childShards.nonEmpty) None
                                  else
                                    Some(
                                      ShardIterator.create(
                                        parts.streamName,
                                        parts.shardId,
                                        parts.sequenceNumber,
                                        now
                                      )
                                    ),
                                  Queue.empty
                                )
                              )

                            case index =>
                              val maxRecords = limit.getOrElse(10000)
                              val firstIndex = index + 1
                              val lastIndex =
                                Math.min(
                                  firstIndex + maxRecords,
                                  data.length
                                )

                              val (head, records) = GetRecordsRequest
                                .getRecords(
                                  data.slice(firstIndex, lastIndex),
                                  Queue.empty,
                                  data(firstIndex),
                                  0
                                )

                              val millisBehindLatest =
                                data.last.approximateArrivalTimestamp.toEpochMilli -
                                  head.approximateArrivalTimestamp.toEpochMilli

                              Right(
                                GetRecordsResponse(
                                  if (
                                    data.lastOption == records.lastOption && data.lastOption.nonEmpty && childShards.nonEmpty
                                  )
                                    Some(childShards)
                                  else None,
                                  millisBehindLatest,
                                  if (
                                    data.lastOption == records.lastOption && data.lastOption.nonEmpty && childShards.nonEmpty
                                  )
                                    None
                                  else
                                    Some(
                                      ShardIterator.create(
                                        parts.streamName,
                                        parts.shardId,
                                        records.last.sequenceNumber,
                                        now
                                      )
                                    ),
                                  records
                                )
                              )
                          }
                        }
                      }

                    }
                }
              )
          )
      }
    }
  }
}

object GetRecordsRequest {
  val maxReturnSize: Int = 10 * 1024 * 1024 // 10 MB

  @tailrec
  def getRecords(
      data: Vector[KinesisRecord],
      results: Queue[KinesisRecord],
      headResult: KinesisRecord,
      totalSize: Int
  ): (KinesisRecord, Queue[KinesisRecord]) = data.headOption match {
    case None => (headResult, results)
    case Some(head) if head.size + totalSize > maxReturnSize =>
      (headResult, results)
    case Some(head) =>
      getRecords(
        data.tail,
        results.enqueue(head),
        head,
        totalSize + head.size
      )
  }

  given getRecordsRequestCirceEncoder: circe.Encoder[GetRecordsRequest] =
    circe.Encoder.forProduct3("Limit", "ShardIterator", "StreamARN")(x =>
      (x.limit, x.shardIterator, x.streamArn)
    )

  given getRecordsRequestCirceDecoder: circe.Decoder[GetRecordsRequest] =
    x =>
      for {
        limit <- x.downField("Limit").as[Option[Int]]
        shardIterator <- x.downField("ShardIterator").as[ShardIterator]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      } yield GetRecordsRequest(limit, shardIterator, streamArn)
  given getRecordsRequestEncoder: Encoder[GetRecordsRequest] =
    Encoder.derive
  given getRecordsRequestDecoder: Decoder[GetRecordsRequest] =
    Decoder.derive
  given getRecordsRequestEq: Eq[GetRecordsRequest] =
    Eq.fromUniversalEquals
}
