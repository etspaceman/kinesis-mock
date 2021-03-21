package kinesis.mock.api

import java.time.Instant

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._

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
      streamData.shards.keys.filter(_.isOpen).size,
      streamData.retentionPeriod.toHours.toInt,
      streamData.streamArn,
      streamData.streamCreationTimestamp,
      streamData.streamName,
      streamData.streamStatus
    )

  implicit val streamDescriptionSummaryCirceEncoder
      : Encoder[StreamDescriptionSummary] = Encoder.forProduct10(
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

  implicit val streamDescriptionSummaryCirceDecoder
      : Decoder[StreamDescriptionSummary] = x =>
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

  implicit val streamDescriptionSummaryEq: Eq[StreamDescriptionSummary] =
    Eq.fromUniversalEquals
}
