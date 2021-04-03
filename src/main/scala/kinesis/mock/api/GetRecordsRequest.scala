package kinesis.mock
package api

import scala.annotation.tailrec

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class GetRecordsRequest(
    limit: Option[Int],
    shardIterator: ShardIterator
) {
  def getRecords(
      streams: Streams
  ): ValidatedNel[KinesisMockException, GetRecordsResponse] =
    shardIterator.parse.andThen { parts =>
      CommonValidations
        .isStreamActiveOrUpdating(parts.streamName, streams)
        .andThen(_ =>
          CommonValidations
            .findStream(parts.streamName, streams)
            .andThen(stream =>
              CommonValidations.findShard(parts.shardId, stream).andThen { case (shard, data) =>
                (limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case None    => Valid(())
                }).andThen { _ =>
                  CommonValidations
                    .isShardOpen(shard)
                    .andThen { _ =>
                      val allShards = stream.shards.keys.toList
                      val childShards = allShards
                        .filter(_.parentShardId.contains(shard.shardId.shardId))
                        .map(s =>
                          ChildShard.fromShard(
                            s,
                            allShards
                              .filter(
                                _.parentShardId.contains(s.shardId.shardId)
                              )
                          )
                        )
                      if (data.isEmpty) {
                        Valid(
                          GetRecordsResponse(
                            childShards,
                            0L,
                            shardIterator,
                            data
                          )
                        )
                      } else {
                        if (
                          parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber
                        ) {
                          val maxRecords = limit.getOrElse(10000)
                          val firstIndex = 0
                          val lastIndex =
                            Math.min(
                              firstIndex + maxRecords,
                              data.length
                            )

                          val records = GetRecordsRequest
                            .getRecords(
                              data.slice(firstIndex, lastIndex),
                              maxRecords,
                              List.empty,
                              0,
                              0
                            )

                          val millisBehindLatest =
                            data.last.approximateArrivalTimestamp.toEpochMilli -
                              records.head.approximateArrivalTimestamp.toEpochMilli

                          Valid(
                            GetRecordsResponse(
                              childShards,
                              millisBehindLatest,
                              ShardIterator.create(
                                parts.streamName,
                                parts.shardId,
                                records.last.sequenceNumber
                              ),
                              records
                            )
                          )
                        } else {
                          data
                            .find(
                              _.sequenceNumber == parts.sequenceNumber
                            ) match {
                            case Some(record) if record == data.last =>
                              Valid(
                                GetRecordsResponse(
                                  childShards,
                                  0L,
                                  shardIterator,
                                  List.empty
                                )
                              )

                            case Some(record) =>
                              val maxRecords = limit.getOrElse(10000)
                              val firstIndex = data.indexOf(record) + 1
                              val lastIndex =
                                Math.min(
                                  firstIndex + maxRecords,
                                  data.length
                                )

                              val records = GetRecordsRequest
                                .getRecords(
                                  data.slice(firstIndex, lastIndex),
                                  maxRecords,
                                  List.empty,
                                  0,
                                  0
                                )

                              val millisBehindLatest =
                                data.last.approximateArrivalTimestamp.toEpochMilli -
                                  record.approximateArrivalTimestamp.toEpochMilli

                              Valid(
                                GetRecordsResponse(
                                  childShards,
                                  millisBehindLatest,
                                  ShardIterator.create(
                                    parts.streamName,
                                    parts.shardId,
                                    records.last.sequenceNumber
                                  ),
                                  records
                                )
                              )

                            case None =>
                              ResourceNotFoundException(
                                s"Record for provided SequenceNumber not found"
                              ).invalidNel
                          }
                        }
                      }
                    }
                }
              }
            )
        )
    }
}

object GetRecordsRequest {
  val maxReturnSize = 10 * 1024 * 1024 // 10 MB

  @tailrec
  def getRecords(
      data: List[KinesisRecord],
      maxRecords: Int,
      results: List[KinesisRecord],
      totalSize: Int,
      totalRecords: Int
  ): List[KinesisRecord] = data match {
    case Nil =>
      results
    case head :: _
        if head.size + totalSize > maxReturnSize || totalRecords + 1 > maxRecords =>
      results
    case head :: tail =>
      getRecords(
        tail,
        maxRecords,
        results :+ head,
        totalSize + head.size,
        totalRecords + 1
      )
  }

  implicit val getRecordsRequestCirceEncoder: Encoder[GetRecordsRequest] =
    Encoder.forProduct2("Limit", "ShardIterator")(x =>
      (x.limit, x.shardIterator)
    )

  implicit val getRecordsRequestCirceDecoder: Decoder[GetRecordsRequest] =
    x =>
      for {
        limit <- x.downField("Limit").as[Option[Int]]
        shardIterator <- x.downField("ShardIterator").as[ShardIterator]
      } yield GetRecordsRequest(limit, shardIterator)

  implicit val getRecordsRequestEq: Eq[GetRecordsRequest] =
    Eq.fromUniversalEquals
}
