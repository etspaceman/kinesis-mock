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
    shardIterator: ShardIterator
) {
  def getRecords(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[GetRecordsResponse]] = streamsRef.get.map { streams =>
    shardIterator.parse.flatMap { parts =>
      val streamArn = StreamArn(awsRegion, parts.streamName, awsAccountId)
      CommonValidations
        .isStreamActiveOrUpdating(streamArn, streams)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamArn, streams)
            .flatMap(stream =>
              CommonValidations.findShard(parts.shardId, stream).flatMap { case (shard, data) =>
                (limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case None    => Right(())
                }).flatMap { _ =>
                  CommonValidations
                    .isShardOpen(shard)
                    .flatMap { _ =>
                      val allShards = stream.shards.keys.toVector
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
                        Right(
                          GetRecordsResponse(
                            childShards,
                            0L,
                            shardIterator,
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
                                  childShards,
                                  0L,
                                  shardIterator,
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

  implicit val getRecordsRequestCirceEncoder: circe.Encoder[GetRecordsRequest] =
    circe.Encoder.forProduct2("Limit", "ShardIterator")(x =>
      (x.limit, x.shardIterator)
    )

  implicit val getRecordsRequestCirceDecoder: circe.Decoder[GetRecordsRequest] =
    x =>
      for {
        limit <- x.downField("Limit").as[Option[Int]]
        shardIterator <- x.downField("ShardIterator").as[ShardIterator]
      } yield GetRecordsRequest(limit, shardIterator)
  implicit val getRecordsRequestEncoder: Encoder[GetRecordsRequest] =
    Encoder.derive
  implicit val getRecordsRequestDecoder: Decoder[GetRecordsRequest] =
    Decoder.derive
  implicit val getRecordsRequestEq: Eq[GetRecordsRequest] =
    Eq.fromUniversalEquals
}
