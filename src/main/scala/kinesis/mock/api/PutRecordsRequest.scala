package kinesis.mock
package api

import scala.collection.mutable.HashMap

import java.time.Instant

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
    streamsRef.modify[Response[PutRecordsResponse]] { streams =>
      val streamNameArn: Response[(StreamName, StreamArn)] =
        (streamName, streamArn) match {
          case (_, Some(arn)) =>
            Right((arn.streamName, arn))
          case (Some(name), _) =>
            Right((name, StreamArn(awsRegion, name, awsAccountId)))
          case _ =>
            Left(
              InvalidArgumentException(
                "Neither streamArn or streamName was provided"
              )
            )
        }
      val now = Instant.now()
      streamNameArn
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
                        CommonValidations.validatePartitionKey(x.partitionKey),
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
                Some(records.length + index),
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
      x.streamName == y.streamName
}
