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

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

final case class CreateStreamRequest(
    shardCount: Option[Int],
    streamModeDetails: Option[StreamModeDetails],
    streamName: StreamName,
    tags: Option[Tags],
    maxRecordSizeInKiB: Option[Int],
    warmThroughputMiBps: Option[Int]
):
  def createStream(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      onDemandStreamCountLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    Utils.now.flatMap { now =>
      streamsRef.modify { streams =>
        val shardCountOrDefault = shardCount.getOrElse(4)
        val streamArn = StreamArn(awsRegion, streamName, awsAccountId)
        val isOnDemand = streamModeDetails
          .map(_.streamMode)
          .getOrElse(StreamMode.PROVISIONED) === StreamMode.ON_DEMAND
        (
          CommonValidations.validateStreamName(streamName),
          if streams.streams.contains(streamArn) then
            ResourceInUseException(
              s"Stream $streamName already exists"
            ).asLeft
          else Right(()),
          CommonValidations.validateShardCount(shardCountOrDefault),
          if streams.streams.count { case (_, stream) =>
              stream.streamStatus == StreamStatus.CREATING
            } >= 5
          then
            LimitExceededException(
              "Limit for streams being created concurrently exceeded"
            ).asLeft
          else Right(()),
          CommonValidations.validateOnDemandStreamCount(
            streams,
            onDemandStreamCountLimit
          ),
          CommonValidations.validateShardLimit(
            shardCountOrDefault,
            streams,
            shardLimit
          ),
          maxRecordSizeInKiB match
            case Some(v) if v < 1024 || v > 10240 =>
              InvalidArgumentException(
                "MaxRecordSizeInKiB must be between 1024 and 10240"
              ).asLeft
            case _ => Right(())
          ,
          warmThroughputMiBps match
            case Some(v) if v < 0 =>
              InvalidArgumentException(
                "WarmThroughputMiBps must be non-negative"
              ).asLeft
            case Some(_) if !isOnDemand =>
              InvalidArgumentException(
                "WarmThroughputMiBps is only valid for on-demand streams"
              ).asLeft
            case _ => Right(())
          ,
          tags match
            case Some(t) => CommonValidations.validateTags(t, Tags.empty)
            case None    => Right(Tags.empty)
        ).mapN { (_, _, _, _, _, _, _, _, _) =>
          val newStream =
            StreamData
              .create(
                shardCountOrDefault,
                streamArn,
                streamModeDetails,
                now
              )
              .copy(
                tags = tags.getOrElse(Tags.empty),
                maxRecordSizeInKiB = maxRecordSizeInKiB,
                warmThroughputMiBps = warmThroughputMiBps
              )
          (
            streams
              .copy(streams = streams.streams ++ Seq(streamArn -> newStream)),
            ()
          )
        }.sequenceWithDefault(streams)
      }
    }

object CreateStreamRequest:
  given createStreamRequestCirceEncoder: circe.Encoder[CreateStreamRequest] =
    circe.Encoder.forProduct6(
      "ShardCount",
      "StreamModeDetails",
      "StreamName",
      "Tags",
      "MaxRecordSizeInKiB",
      "WarmThroughputMiBps"
    )(x =>
      (
        x.shardCount,
        x.streamModeDetails,
        x.streamName,
        x.tags,
        x.maxRecordSizeInKiB,
        x.warmThroughputMiBps
      )
    )
  given createStreamRequestCirceDecoder: circe.Decoder[CreateStreamRequest] =
    x =>
      for
        shardCount <- x.downField("ShardCount").as[Option[Int]]
        streamModeDetails <- x
          .downField("StreamModeDetails")
          .as[Option[StreamModeDetails]]
        streamName <- x.downField("StreamName").as[StreamName]
        tags <- x.downField("Tags").as[Option[Tags]]
        maxRecordSizeInKiB <- x
          .downField("MaxRecordSizeInKiB")
          .as[Option[Int]]
        warmThroughputMiBps <- x
          .downField("WarmThroughputMiBps")
          .as[Option[Int]]
      yield CreateStreamRequest(
        shardCount,
        streamModeDetails,
        streamName,
        tags,
        maxRecordSizeInKiB,
        warmThroughputMiBps
      )
  given createStreamRequestEncoder: Encoder[CreateStreamRequest] =
    Encoder.derive
  given createStreamRequestDecoder: Decoder[CreateStreamRequest] =
    Decoder.derive

  given Eq[CreateStreamRequest] =
    Eq.fromUniversalEquals
