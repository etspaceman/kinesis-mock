package kinesis.mock.models

import scala.collection.SortedMap
import scala.concurrent.duration._

import java.time.Instant

final case class StreamData(
    consumers: SortedMap[ConsumerName, Consumer],
    encryptionType: EncryptionType,
    enhancedMonitoring: List[ShardLevelMetrics],
    keyId: Option[String],
    retentionPeriod: FiniteDuration,
    shards: SortedMap[Shard, List[KinesisRecord]],
    streamArn: String,
    streamCreationTimestamp: Instant,
    streamName: StreamName,
    streamStatus: StreamStatus,
    tags: Tags,
    shardCountUpdates: List[Instant]
)

object StreamData {
  val minRetentionPeriod = 24.hours
  val maxRetentionPeriod = 365.days

  def create(
      shardCount: Int,
      streamName: StreamName,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): (StreamData, List[ShardSemaphoresKey]) = {

    val createTime = Instant.now()
    val shards: SortedMap[Shard, List[KinesisRecord]] =
      Shard.newShards(shardCount, createTime, 0)
    (
      StreamData(
        SortedMap.empty,
        EncryptionType.NONE,
        List(ShardLevelMetrics(List.empty)),
        None,
        minRetentionPeriod,
        shards,
        s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName",
        Instant.now(),
        streamName,
        StreamStatus.CREATING,
        Tags.empty,
        List.empty
      ),
      shards.keys.toList.map(shard => ShardSemaphoresKey(streamName, shard))
    )
  }
}
