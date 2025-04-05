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
package models

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe.*

final case class StreamDescriptionSummary(
    consumerCount: Option[Int],
    encryptionType: Option[EncryptionType],
    enhancedMonitoring: Vector[ShardLevelMetrics],
    keyId: Option[String],
    openShardCount: Int,
    retentionPeriodHours: Int,
    streamArn: StreamArn,
    streamCreationTimestamp: Instant,
    streamModeDetails: StreamModeDetails,
    streamName: StreamName,
    streamStatus: StreamStatus
)

object StreamDescriptionSummary:
  def fromStreamData(streamData: StreamData): StreamDescriptionSummary =
    StreamDescriptionSummary(
      Some(streamData.consumers.size),
      Some(streamData.encryptionType),
      streamData.enhancedMonitoring,
      streamData.keyId,
      streamData.shards.keys.count(_.isOpen),
      streamData.retentionPeriod.toHours.toInt,
      streamData.streamArn,
      streamData.streamCreationTimestamp,
      streamData.streamModeDetails,
      streamData.streamName,
      streamData.streamStatus
    )

  def streamDescriptionSummaryCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[StreamDescriptionSummary] = circe.Encoder.forProduct11(
    "ConsumerCount",
    "EncryptionType",
    "EnhancedMonitoring",
    "KeyId",
    "OpenShardCount",
    "RetentionPeriodHours",
    "StreamARN",
    "StreamCreationTimestamp",
    "StreamModeDetails",
    "StreamName",
    "StreamStatus"
  )(x =>
    (
      x.consumerCount,
      x.encryptionType,
      x.enhancedMonitoring,
      x.keyId,
      x.openShardCount,
      x.retentionPeriodHours,
      x.streamArn,
      x.streamCreationTimestamp,
      x.streamModeDetails,
      x.streamName,
      x.streamStatus
    )
  )

  def streamDescriptionSummaryCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[StreamDescriptionSummary] = x =>
    for
      consumerCount <- x.downField("ConsumerCount").as[Option[Int]]
      encryptionType <- x.downField("EncryptionType").as[Option[EncryptionType]]
      enhancedMonitoring <- x
        .downField("EnhancedMonitoring")
        .as[Vector[ShardLevelMetrics]]
      keyId <- x.downField("KeyId").as[Option[String]]
      openShardCount <- x.downField("OpenShardCount").as[Int]
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      streamArn <- x.downField("StreamARN").as[StreamArn]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Instant]
      streamModeDetails <- x
        .downField("StreamModeDetails")
        .as[StreamModeDetails]
      streamName <- x.downField("StreamName").as[StreamName]
      streamStatus <- x.downField("StreamStatus").as[StreamStatus]
    yield StreamDescriptionSummary(
      consumerCount,
      encryptionType,
      enhancedMonitoring,
      keyId,
      openShardCount,
      retentionPeriodHours,
      streamArn,
      streamCreationTimestamp,
      streamModeDetails,
      streamName,
      streamStatus
    )

  given streamDescriptionSummaryEncoder: Encoder[StreamDescriptionSummary] =
    Encoder.instance(
      streamDescriptionSummaryCirceEncoder(instantDoubleCirceEncoder),
      streamDescriptionSummaryCirceEncoder(instantLongCirceEncoder)
    )

  given streamDescriptionSummaryDecoder: Decoder[StreamDescriptionSummary] =
    Decoder.instance(
      streamDescriptionSummaryCirceDecoder(instantDoubleCirceDecoder),
      streamDescriptionSummaryCirceDecoder(instantLongCirceDecoder)
    )

  given streamDescriptionSummaryEq: Eq[StreamDescriptionSummary] =
    (x, y) =>
      x.consumerCount == y.consumerCount &&
        x.encryptionType == y.encryptionType &&
        x.enhancedMonitoring == y.enhancedMonitoring &&
        x.keyId == y.keyId &&
        x.openShardCount == y.openShardCount &&
        x.retentionPeriodHours == y.retentionPeriodHours &&
        x.streamArn == y.streamArn &&
        x.streamCreationTimestamp.getEpochSecond == y.streamCreationTimestamp.getEpochSecond &&
        x.streamName == y.streamName &&
        x.streamStatus == y.streamStatus
