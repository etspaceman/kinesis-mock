package kinesis.mock.models

import java.time.Instant

final case class StreamData(
    name: String,
    data: Map[Shard, List[KinesisRecord]],
    tags: Map[String, String],
    status: StreamStatus,
    encryptionType: EncryptionType,
    enhancedMonitoring: List[ShardLevelMetrics],
    keyId: Option[String],
    streamArn: String,
    streamCreationTimestamp: Instant
)

object StreamData {
  def create(
      shardCount: Int,
      streamName: String,
      awsRegion: AwsRegion,
      awsAccountId: String
  ): StreamData = StreamData(
    streamName,
    ???,
    Map.empty,
    StreamStatus.CREATING,
    EncryptionType.NONE,
    List(ShardLevelMetrics(List.empty)),
    None,
    s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName",
    Instant.now()
  )
}
