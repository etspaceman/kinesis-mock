package kinesis.mock
package models

import scala.collection.SortedMap
import scala.concurrent.duration._

import java.time.Instant

import cats.Eq
import cats.syntax.all._
import io.circe
import io.circe.derivation._

import kinesis.mock.instances.circe._

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

  implicit val streamDataCirceEncoder: circe.Encoder[StreamData] = deriveEncoder
  implicit val streamDataCirceDecoder: circe.Decoder[StreamData] = deriveDecoder

  implicit val streamDataEq: Eq[StreamData] = (x, y) =>
    x.consumers.toMap === y.consumers.toMap &&
      x.encryptionType == y.encryptionType &&
      x.enhancedMonitoring == y.enhancedMonitoring &&
      x.keyId == y.keyId &&
      x.retentionPeriod == y.retentionPeriod &&
      x.shards.toMap === y.shards.toMap &&
      x.streamArn == y.streamArn &&
      x.streamCreationTimestamp.getEpochSecond == y.streamCreationTimestamp.getEpochSecond &&
      x.streamName == y.streamName &&
      x.streamStatus == y.streamStatus &&
      x.tags == y.tags &&
      x.shardCountUpdates.map(_.getEpochSecond) == y.shardCountUpdates.map(
        _.getEpochSecond
      )

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
