package kinesis.mock.models

import scala.collection.SortedMap
import scala.concurrent.duration._

import java.time.Instant

final case class StreamData(
    consumers: Map[String, Consumer],
    encryptionType: EncryptionType,
    enhancedMonitoring: List[ShardLevelMetrics],
    keyId: Option[String],
    retentionPeriod: FiniteDuration,
    shards: SortedMap[Shard, List[KinesisRecord]],
    streamArn: String,
    streamCreationTimestamp: Instant,
    streamName: String,
    streamStatus: StreamStatus,
    tags: Map[String, String]
)

object StreamData {
  val seqAdjustMs = 2000L
  val minHashKey: BigInt = BigInt(0)
  val maxHashKey: BigInt = BigInt("340282366920938463463374607431768211455")
  val minRetentionPeriod = 24.hours
  val maxRetentionPeriod = 365.days

  def create(
      shardCount: Int,
      streamName: String,
      awsRegion: AwsRegion,
      awsAccountId: String
  ): (StreamData, List[ShardSemaphoresKey]) = {
    val shardHash = maxHashKey / BigInt(shardCount)
    val createTime = Instant.now().minusMillis(seqAdjustMs)
    val shards: SortedMap[Shard, List[KinesisRecord]] =
      SortedMap.from(
        List
          .range(0, shardCount, 1)
          .map(index =>
            Shard(
              None,
              None,
              createTime,
              HashKeyRange(
                if (index < shardCount - 1) shardHash * BigInt(index + 1)
                else maxHashKey - BigInt(1),
                shardHash * BigInt(index)
              ),
              None,
              SequenceNumberRange(
                None,
                SequenceNumber.create(createTime, index, None, None, None)
              ),
              Shard.shardId(index),
              index
            ) -> List.empty
          )
      )
    (
      StreamData(
        Map.empty,
        EncryptionType.NONE,
        List(ShardLevelMetrics(List.empty)),
        None,
        minRetentionPeriod,
        shards,
        s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName",
        Instant.now(),
        streamName,
        StreamStatus.CREATING,
        Map.empty
      ),
      shards.keys.toList.map(shard => ShardSemaphoresKey(streamName, shard))
    )
  }
}
