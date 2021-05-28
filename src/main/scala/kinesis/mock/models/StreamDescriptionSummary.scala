package kinesis.mock
package models

import java.time.Instant

import cats.kernel.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class StreamDescriptionSummary(
    consumerCount: Option[Int],
    encryptionType: Option[EncryptionType],
    enhancedMonitoring: List[ShardLevelMetrics],
    keyId: Option[String],
    openShardCount: Int,
    retentionPeriodHours: Int,
    streamArn: String,
    streamCreationTimestamp: Instant,
    streamName: StreamName,
    streamStatus: StreamStatus
)

object StreamDescriptionSummary {
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
      streamData.streamName,
      streamData.streamStatus
    )

  def streamDescriptionSummaryCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[StreamDescriptionSummary] = circe.Encoder.forProduct10(
    "ConsumerCount",
    "EncryptionType",
    "EnhancedMonitoring",
    "KeyId",
    "OpenShardCount",
    "RetentionPeriodHours",
    "StreamARN",
    "StreamCreationTimestamp",
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
      x.streamName,
      x.streamStatus
    )
  )

  def streamDescriptionSummaryCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[StreamDescriptionSummary] = x =>
    for {
      consumerCount <- x.downField("ConsumerCount").as[Option[Int]]
      encryptionType <- x.downField("EncryptionType").as[Option[EncryptionType]]
      enhancedMonitoring <- x
        .downField("EnhancedMonitoring")
        .as[List[ShardLevelMetrics]]
      keyId <- x.downField("KeyId").as[Option[String]]
      openShardCount <- x.downField("OpenShardCount").as[Int]
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      streamArn <- x.downField("StreamARN").as[String]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Instant]
      streamName <- x.downField("StreamName").as[StreamName]
      streamStatus <- x.downField("StreamStatus").as[StreamStatus]
    } yield StreamDescriptionSummary(
      consumerCount,
      encryptionType,
      enhancedMonitoring,
      keyId,
      openShardCount,
      retentionPeriodHours,
      streamArn,
      streamCreationTimestamp,
      streamName,
      streamStatus
    )

  implicit val streamDescriptionSummaryEncoder
      : Encoder[StreamDescriptionSummary] = Encoder.instance(
    streamDescriptionSummaryCirceEncoder(instantDoubleCirceEncoder),
    streamDescriptionSummaryCirceEncoder(instantLongCirceEncoder)
  )

  implicit val streamDescriptionSummaryDecoder
      : Decoder[StreamDescriptionSummary] = Decoder.instance(
    streamDescriptionSummaryCirceDecoder(instantDoubleCirceDecoder),
    streamDescriptionSummaryCirceDecoder(instantLongCirceDecoder)
  )

  implicit val streamDescriptionSummaryEq: Eq[StreamDescriptionSummary] =
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
}
