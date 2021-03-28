package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class MergeShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should merge shards")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(5, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        adjacentParentShardId = ShardId.create(1).shardId
        parentShardId = ShardId.create(0).shardId
        _ <- cache
          .mergeShards(
            MergeShardsRequest(
              adjacentParentShardId,
              parentShardId,
              streamName
            )
          )
          .rethrow
        describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache
          .describeStreamSummary(describeStreamSummaryReq)
          .rethrow
        _ <- IO.sleep(cacheConfig.mergeShardsDuration.plus(10.millis))
        checkStream2 <- cache
          .describeStreamSummary(describeStreamSummaryReq)
          .rethrow
        checkShards <- cache
          .listShards(
            ListShardsRequest(
              None,
              None,
              None,
              None,
              None,
              Some(streamName)
            )
          )
          .rethrow
      } yield assert(
        checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
          checkShards.shards.count(!_.isOpen) == 2 &&
          checkShards.shards.exists(shard =>
            shard.adjacentParentShardId.contains(
              adjacentParentShardId
            ) && shard.parentShardId.contains(parentShardId)
          ) && checkShards.shards.length == 6,
        s"${checkShards.shards.mkString("\n\t")}\n" +
          s"$checkStream1\n" +
          s"$checkStream2"
      )
  })
}
