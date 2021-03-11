package kinesis.mock.models

import scala.concurrent.duration._

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
    streamCreationTimestamp: Instant,
    consumers: List[Consumer],
    retentionPeriod: FiniteDuration
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
  ): StreamData = {
    val shardHash = maxHashKey / BigInt(shardCount)
    val createTime = Instant.now().minusMillis(seqAdjustMs)
    val shards: Map[Shard, List[KinesisRecord]] = List
      .range(0, shardCount, 1)
      .map(index =>
        Shard(
          "shardId-" + s"00000000000$index".takeRight(12),
          HashKeyRange(
            shardHash * BigInt(index),
            if (index < shardCount - 1) shardHash * BigInt(index + 1)
            else maxHashKey - BigInt(1)
          ),
          SequenceNumberRange(
            SequenceNumberRange
              .stringifySequence(createTime, index, None, None, None),
            None
          )
        ) -> List.empty
      )
      .toMap
    StreamData(
      streamName,
      shards,
      Map.empty,
      StreamStatus.CREATING,
      EncryptionType.NONE,
      List(ShardLevelMetrics(List.empty)),
      None,
      s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName",
      Instant.now(),
      List.empty,
      minRetentionPeriod
    )
  }
}
