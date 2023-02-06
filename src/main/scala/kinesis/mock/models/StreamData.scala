package kinesis.mock
package models

import scala.collection.SortedMap
import scala.concurrent.duration._

import java.time.Instant

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.instances.circe._

final case class StreamData(
    consumers: SortedMap[ConsumerName, Consumer],
    encryptionType: EncryptionType,
    enhancedMonitoring: Option[Vector[ShardLevelMetrics]],
    keyId: Option[String],
    retentionPeriod: FiniteDuration,
    shards: SortedMap[Shard, Vector[KinesisRecord]],
    streamArn: StreamArn,
    streamCreationTimestamp: Instant,
    streamModeDetails: StreamModeDetails,
    streamName: StreamName,
    streamStatus: StreamStatus,
    tags: Tags,
    shardCountUpdates: Vector[Instant]
)

object StreamData {
  val minRetentionPeriod: FiniteDuration = 24.hours
  val maxRetentionPeriod: FiniteDuration = 365.days

  private implicit val consumerCirceEncoder: circe.Encoder[Consumer] =
    Encoder[Consumer].circeEncoder
  private implicit val kinesisRecordCirceEncoder: circe.Encoder[KinesisRecord] =
    Encoder[KinesisRecord].circeEncoder
  private implicit val consumerCirceDecoder: circe.Decoder[Consumer] =
    Decoder[Consumer].circeDecoder
  private implicit val kinesisRecordCirceDecoder: circe.Decoder[KinesisRecord] =
    Decoder[KinesisRecord].circeDecoder

  implicit val streamDataCirceEncoder: circe.Encoder[StreamData] =
    circe.Encoder.forProduct13(
      "consumers",
      "encryptionType",
      "enhancedMonitoring",
      "keyId",
      "retentionPeriod",
      "shards",
      "streamArn",
      "streamCreationTimestamp",
      "streamModeDetails",
      "streamName",
      "streamStatus",
      "tags",
      "shardCountUpdates"
    )(x =>
      (
        x.consumers,
        x.encryptionType,
        x.enhancedMonitoring,
        x.keyId,
        x.retentionPeriod,
        x.shards,
        x.streamArn,
        x.streamCreationTimestamp,
        x.streamModeDetails,
        x.streamName,
        x.streamStatus,
        x.tags,
        x.shardCountUpdates
      )
    )
  implicit val streamDataCirceDecoder: circe.Decoder[StreamData] = { x =>
    for {
      consumers <- x
        .downField("consumers")
        .as[SortedMap[ConsumerName, Consumer]]
      encryptionType <- x.downField("encryptionType").as[EncryptionType]
      enhancedMonitoring <- x
        .downField("enhancedMonitoring")
        .as[Option[Vector[ShardLevelMetrics]]]
      keyId <- x.downField("keyId").as[Option[String]]
      retentionPeriod <- x.downField("retentionPeriod").as[FiniteDuration]
      shards <- x
        .downField("shards")
        .as[SortedMap[Shard, Vector[KinesisRecord]]]
      streamArn <- x.downField("streamArn").as[StreamArn]
      streamCreationTimestamp <- x
        .downField("streamCreationTimestamp")
        .as[Instant]
      streamModeDetails <- x
        .downField("streamModeDetails")
        .as[StreamModeDetails]
      streamName <- x.downField("streamName").as[StreamName]
      streamStatus <- x.downField("streamStatus").as[StreamStatus]
      tags <- x.downField("tags").as[Tags]
      shardCountUpdates <- x.downField("shardCountUpdates").as[Vector[Instant]]
    } yield StreamData(
      consumers,
      encryptionType,
      enhancedMonitoring,
      keyId,
      retentionPeriod,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamModeDetails,
      streamName,
      streamStatus,
      tags,
      shardCountUpdates
    )

  }

  implicit val streamDataEq: Eq[StreamData] = (x, y) =>
    x.consumers.toMap === y.consumers.toMap &&
      x.encryptionType === y.encryptionType &&
      x.enhancedMonitoring === y.enhancedMonitoring &&
      x.keyId === y.keyId &&
      x.retentionPeriod === y.retentionPeriod &&
      x.shards.toMap === y.shards.toMap &&
      x.streamArn === y.streamArn &&
      x.streamCreationTimestamp.getEpochSecond == y.streamCreationTimestamp.getEpochSecond &&
      x.streamModeDetails === y.streamModeDetails &&
      x.streamName === y.streamName &&
      x.streamStatus === y.streamStatus &&
      x.tags === y.tags &&
      x.shardCountUpdates.map(_.getEpochSecond) === y.shardCountUpdates.map(
        _.getEpochSecond
      )

  def create(
      shardCount: Int,
      streamArn: StreamArn,
      streamModeDetails: Option[StreamModeDetails]
  ): StreamData = {
    val createTime = Instant.now()
    val shards: SortedMap[Shard, Vector[KinesisRecord]] =
      Shard.newShards(shardCount, createTime, 0)
    StreamData(
      SortedMap.empty,
      EncryptionType.NONE,
      None,
      None,
      minRetentionPeriod,
      shards,
      streamArn,
      Instant.now(),
      streamModeDetails.getOrElse(StreamModeDetails(StreamMode.PROVISIONED)),
      streamArn.streamName,
      StreamStatus.CREATING,
      Tags.empty,
      Vector.empty
    )
  }
}
