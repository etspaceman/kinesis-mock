/*
 * Copyright 2021-2026 io.github.etspaceman
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

import scala.collection.immutable.Queue
import scala.concurrent.duration.*

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import io.circe
import io.circe.Printer
import io.circe.syntax.*
import scodec.bits.ByteVector

import kinesis.mock.cache.SubscriptionRegistry
import kinesis.mock.eventstream.EventStreamMessage
import kinesis.mock.instances.circe.*
import kinesis.mock.models.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SubscribeToShard.html
final case class SubscribeToShardRequest(
    consumerArn: ConsumerArn,
    shardId: String,
    startingPosition: StartingPosition
):
  def subscribeToShard(
      streamsRef: Ref[IO, Streams],
      subscriptionRegistry: SubscriptionRegistry
  ): IO[Stream[IO, Byte]] =
    streamsRef.get.map { streams =>
      val validation = CommonValidations
        .findStreamByConsumerArn(consumerArn, streams)
        .flatMap { case (_, stream) =>
          CommonValidations
            .isStreamActiveOrUpdating(stream.streamArn, streams)
            .as(stream)
        }

      validation match
        case Left(err) =>
          Stream
            .chunk(
              Chunk.byteVector(SubscribeToShardRequest.exceptionFrame(err))
            )
            .covary[IO]
        case Right(stream) =>
          Stream
            .resource(subscriptionRegistry.lease(consumerArn, shardId))
            .flatMap {
              case None =>
                Stream.chunk(
                  Chunk.byteVector(
                    SubscribeToShardRequest.exceptionFrame(
                      ResourceInUseException(
                        s"Another active subscription exists for consumer ${consumerArn.consumerArn} on shard $shardId"
                      )
                    )
                  )
                )
              case Some(_) =>
                subscriptionStream(streamsRef, stream)
            }
    }

  private def subscriptionStream(
      streamsRef: Ref[IO, Streams],
      stream: StreamData
  ): Stream[IO, Byte] =
    val startingSeq = stream.shards.keys
      .find(_.shardId.shardId == shardId)
      .map(_.sequenceNumberRange.startingSequenceNumber)
      .getOrElse(SequenceNumber("0"))

    val initialData = SubscribeToShardRequest.shardRecords(stream, shardId)
    val initialIndex =
      SubscribeToShardRequest.resolveStartingIndex(
        initialData,
        startingPosition
      )

    given io.circe.Encoder[SubscribeToShardEvent] =
      Encoder[SubscribeToShardEvent].circeEncoder

    Stream
      .eval(Ref.of[IO, Int](initialIndex))
      .flatMap { idxRef =>
        Stream
          .awakeEvery[IO](1.second)
          .evalMap { _ =>
            for
              start <- idxRef.get
              current <- streamsRef.get
              data = current.streams
                .get(stream.streamArn)
                .map(SubscribeToShardRequest.shardRecords(_, shardId))
                .getOrElse(Vector.empty)
              end = math.min(start + 10000, data.length)
              fresh = data.slice(start, end)
              _ <- idxRef.set(end)
              continuationSeq = fresh.lastOption
                .map(_.sequenceNumber)
                .orElse(
                  if start > 0 && start <= data.length then
                    Some(data(start - 1).sequenceNumber)
                  else None
                )
                .getOrElse(startingSeq)
              millisBehindLatest = (data.lastOption, fresh.lastOption) match
                case (Some(last), Some(f)) =>
                  last.approximateArrivalTimestamp.toEpochMilli -
                    f.approximateArrivalTimestamp.toEpochMilli
                case _ => 0L
            yield SubscribeToShardEvent(
              records = Queue.from(fresh),
              continuationSequenceNumber = continuationSeq,
              millisBehindLatest = millisBehindLatest,
              childShards = None
            )
          }
          .map { event =>
            val payload = ByteVector(
              Printer.noSpaces.print(event.asJson).getBytes("UTF-8")
            )
            EventStreamMessage.encode(
              EventStreamMessage.event(
                "SubscribeToShardEvent",
                "application/json",
                payload
              )
            )
          }
          .interruptAfter(5.minutes)
          .flatMap(bv => Stream.chunk(Chunk.byteVector(bv)))
      }

object SubscribeToShardRequest:
  private[api] def shardRecords(
      stream: StreamData,
      shardId: String
  ): Vector[KinesisRecord] =
    stream.shards
      .find(_._1.shardId.shardId == shardId)
      .map(_._2)
      .getOrElse(Vector.empty)

  private[api] def resolveStartingIndex(
      data: Vector[KinesisRecord],
      startingPosition: StartingPosition
  ): Int =
    startingPosition.`type` match
      case ShardIteratorType.LATEST             => data.length
      case ShardIteratorType.TRIM_HORIZON       => 0
      case ShardIteratorType.AT_SEQUENCE_NUMBER =>
        startingPosition.sequenceNumber
          .map(sn => data.indexWhere(_.sequenceNumber == sn))
          .filter(_ >= 0)
          .getOrElse(0)
      case ShardIteratorType.AFTER_SEQUENCE_NUMBER =>
        startingPosition.sequenceNumber
          .map(sn => data.indexWhere(_.sequenceNumber == sn))
          .filter(_ >= 0)
          .map(_ + 1)
          .getOrElse(0)
      case ShardIteratorType.AT_TIMESTAMP =>
        startingPosition.timestamp
          .map(ts =>
            data.indexWhere(!_.approximateArrivalTimestamp.isBefore(ts))
          )
          .filter(_ >= 0)
          .getOrElse(data.length)

  private[api] def exceptionFrame(
      err: KinesisMockException
  ): ByteVector =
    val safeMsg = Option(err.getMessage).getOrElse("").replace("\"", "\\\"")
    val body = ByteVector(s"""{"message":"$safeMsg"}""".getBytes("UTF-8"))
    EventStreamMessage.encode(
      EventStreamMessage.exception(
        err.getClass.getSimpleName,
        "application/json",
        body
      )
    )

  def subscribeToShardRequestCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[SubscribeToShardRequest] =
    given circe.Encoder[StartingPosition] =
      StartingPosition.startingPositionCirceEncoder
    circe.Encoder.forProduct3("ConsumerARN", "ShardId", "StartingPosition")(r =>
      (r.consumerArn, r.shardId, r.startingPosition)
    )

  def subscribeToShardRequestCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[SubscribeToShardRequest] =
    given circe.Decoder[StartingPosition] =
      StartingPosition.startingPositionCirceDecoder
    c =>
      for
        consumerArn <- c.downField("ConsumerARN").as[ConsumerArn]
        shardId <- c.downField("ShardId").as[String]
        startingPosition <- c.downField("StartingPosition").as[StartingPosition]
      yield SubscribeToShardRequest(consumerArn, shardId, startingPosition)

  given subscribeToShardRequestEncoder: Encoder[SubscribeToShardRequest] =
    Encoder.instance(
      subscribeToShardRequestCirceEncoder(using instantBigDecimalCirceEncoder),
      subscribeToShardRequestCirceEncoder(using instantLongCirceEncoder)
    )

  given subscribeToShardRequestDecoder: Decoder[SubscribeToShardRequest] =
    Decoder.instance(
      subscribeToShardRequestCirceDecoder(using instantBigDecimalCirceDecoder),
      subscribeToShardRequestCirceDecoder(using instantLongCirceDecoder)
    )

  given Eq[SubscribeToShardRequest] = (x, y) =>
    x.consumerArn === y.consumerArn &&
      x.shardId == y.shardId &&
      x.startingPosition === y.startingPosition
