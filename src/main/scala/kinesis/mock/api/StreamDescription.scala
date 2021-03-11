package kinesis.mock.api

import java.time.Instant

import io.circe._

import kinesis.mock.models._

final case class StreamDescription(
    encryptionType: EncryptionType,
    enhancedMonitoring: List[ShardLevelMetrics],
    hasMoreShards: Boolean,
    keyId: Option[String],
    retentionPeriodHours: Int,
    shards: List[Shard],
    streamArn: String,
    streamCreationTimestamp: Instant,
    streamName: String,
    streamStatus: StreamStatus
)

object StreamDescription {
  def fromStreamData(
      streamData: StreamData,
      exclusiveStartShardId: Option[String],
      limit: Option[Int]
  ): StreamDescription = {
    val allShards = streamData.shards.keys.toList
    val lim = Math.min(limit.getOrElse(100), 100)

    val (shards: List[Shard], hasMoreShards: Boolean) =
      exclusiveStartShardId match {
        case None =>
          val s = allShards.take(lim)
          (s, allShards.length > s.length)
        case Some(shardId) => {
          val indexOfShard = allShards.indexWhere(_.shardId == shardId)
          val allShardsAfterStart = allShards.splitAt(indexOfShard + 1)._2
          val s = allShardsAfterStart.take(lim)
          (s, allShardsAfterStart.length > s.length)
        }
      }

    StreamDescription(
      streamData.encryptionType,
      streamData.enhancedMonitoring,
      hasMoreShards,
      streamData.keyId,
      streamData.retentionPeriod.toHours.toInt,
      shards,
      streamData.streamArn,
      streamData.streamCreationTimestamp,
      streamData.streamName,
      streamData.streamStatus
    )
  }

  implicit val streamDescriptionCirceEncoder: Encoder[StreamDescription] =
    Encoder.forProduct10(
      "EncryptionType",
      "EnhancedMonitoring",
      "HasMoreShards",
      "KeyId",
      "RetentionPeriodHours",
      "Shards",
      "StreamARN",
      "StreamCreationTimestamp",
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
        x.streamName,
        x.streamStatus
      )
    )

  implicit val streamDescriptionCirceDecoder: Decoder[StreamDescription] = {
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        enhancedMonitoring <- x
          .downField("EnhancedMonitoring")
          .as[List[ShardLevelMetrics]]
        hasMoreShards <- x.downField("HasMoreShards").as[Boolean]
        keyId <- x.downField("KeyId").as[Option[String]]
        retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
        shards <- x.downField("Shards").as[List[Shard]]
        streamArn <- x.downField("StreamARN").as[String]
        streamCreationTimestamp <- x
          .downField("StreamCreationTimestamp")
          .as[Instant]
        streamName <- x.downField("StreamName").as[String]
        streamStatus <- x.downField("StreamStatus").as[StreamStatus]
      } yield StreamDescription(
        encryptionType,
        enhancedMonitoring,
        hasMoreShards,
        keyId,
        retentionPeriodHours,
        shards,
        streamArn,
        streamCreationTimestamp,
        streamName,
        streamStatus
      )
  }
}
