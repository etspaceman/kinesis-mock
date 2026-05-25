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
import cats.syntax.all.*
import io.circe

import kinesis.mock.instances.circe.*

final case class StreamDescription(
    encryptionType: Option[EncryptionType],
    enhancedMonitoring: Vector[ShardLevelMetrics],
    hasMoreShards: Boolean,
    keyId: Option[String],
    retentionPeriodHours: Int,
    shards: Vector[ShardSummary],
    streamArn: StreamArn,
    streamCreationTimestamp: Instant,
    streamModeDetails: StreamModeDetails,
    streamName: StreamName,
    streamStatus: StreamStatus
)

object StreamDescription:
  def fromStreamData(
      streamData: StreamData,
      exclusiveStartShardId: Option[String],
      limit: Option[Int]
  ): StreamDescription =
    val allShards = streamData.shards.keys.toVector
    val lim = Math.min(limit.getOrElse(100), 100)

    val (shards: Vector[Shard], hasMoreShards: Boolean) =
      exclusiveStartShardId match
        case None =>
          val s = allShards.take(lim)
          (s, allShards.length > s.length)
        case Some(shardId) =>
          val indexOfShard = allShards.indexWhere(_.shardId.shardId == shardId)
          val allShardsAfterStart = allShards.splitAt(indexOfShard + 1)._2
          val s = allShardsAfterStart.take(lim)
          (s, allShardsAfterStart.length > s.length)

    StreamDescription(
      Some(streamData.encryptionType),
      streamData.enhancedMonitoring,
      hasMoreShards,
      streamData.keyId,
      streamData.retentionPeriod.toHours.toInt,
      shards.map(ShardSummary.fromShard),
      streamData.streamArn,
      streamData.streamCreationTimestamp,
      streamData.streamModeDetails,
      streamData.streamName,
      streamData.streamStatus
    )

  def streamDescriptionCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[StreamDescription] =
    circe.Encoder.forProduct11(
      "EncryptionType",
      "EnhancedMonitoring",
      "HasMoreShards",
      "KeyId",
      "RetentionPeriodHours",
      "Shards",
      "StreamARN",
      "StreamCreationTimestamp",
      "StreamModeDetails",
      "StreamName",
      "StreamStatus"
    )(x =>
      (
        x.encryptionType,
        x.enhancedMonitoring,
        x.hasMoreShards,
        x.keyId,
        x.retentionPeriodHours,
        x.shards,
        x.streamArn,
        x.streamCreationTimestamp,
        x.streamModeDetails,
        x.streamName,
        x.streamStatus
      )
    )

  def streamDescriptionCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[StreamDescription] = x =>
    for
      encryptionType <- x
        .downField("EncryptionType")
        .as[Option[EncryptionType]]
      enhancedMonitoring <- x
        .downField("EnhancedMonitoring")
        .as[Vector[ShardLevelMetrics]]
      hasMoreShards <- x.downField("HasMoreShards").as[Boolean]
      keyId <- x.downField("KeyId").as[Option[String]]
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      shards <- x.downField("Shards").as[Vector[ShardSummary]]
      streamArn <- x.downField("StreamARN").as[StreamArn]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Instant]
      streamModeDetails <- x
        .downField("StreamModeDetails")
        .as[StreamModeDetails]
      streamName <- x.downField("StreamName").as[StreamName]
      streamStatus <- x.downField("StreamStatus").as[StreamStatus]
    yield StreamDescription(
      encryptionType,
      enhancedMonitoring,
      hasMoreShards,
      keyId,
      retentionPeriodHours,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamModeDetails,
      streamName,
      streamStatus
    )

  given streamDescriptionEncoder: Encoder[StreamDescription] =
    Encoder.instance(
      streamDescriptionCirceEncoder(using instantDoubleCirceEncoder),
      streamDescriptionCirceEncoder(using instantLongCirceEncoder)
    )

  given streamDescriptionDecoder: Decoder[StreamDescription] =
    Decoder.instance(
      streamDescriptionCirceDecoder(using instantDoubleCirceDecoder),
      streamDescriptionCirceDecoder(using instantLongCirceDecoder)
    )

  given Eq[StreamDescription] =
    (x, y) =>
      x.encryptionType == y.encryptionType &&
        x.enhancedMonitoring == y.enhancedMonitoring &&
        x.hasMoreShards == y.hasMoreShards &&
        x.keyId == y.keyId &&
        x.retentionPeriodHours == y.retentionPeriodHours &&
        x.shards === y.shards &&
        x.streamArn == y.streamArn &&
        x.streamCreationTimestamp.getEpochSecond == y.streamCreationTimestamp.getEpochSecond &&
        x.streamName == y.streamName &&
        x.streamStatus == y.streamStatus
