package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class SplitShardTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should split a shard")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(5, streamName), context, false)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
          listShardsReq = ListShardsRequest(
            None,
            None,
            None,
            None,
            None,
            Some(streamName)
          )
          shardToSplit <- cache
            .listShards(listShardsReq, context, false)
            .rethrow
            .map(_.shards.head)
          _ <- cache
            .splitShard(
              SplitShardRequest(
                (shardToSplit.hashKeyRange.endingHashKey / BigInt(2)).toString,
                shardToSplit.shardId,
                streamName
              ),
              context,
              false
            )
            .rethrow
          describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
          checkStream1 <- cache
            .describeStreamSummary(describeStreamSummaryReq, context, false)
            .rethrow
          _ <- IO.sleep(cacheConfig.splitShardDuration.plus(200.millis))
          checkStream2 <- cache
            .describeStreamSummary(describeStreamSummaryReq, context, false)
            .rethrow
          checkShards <- cache.listShards(listShardsReq, context, false).rethrow
        } yield assert(
          checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
            checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
            checkShards.shards.count(!_.isOpen) == 1 &&
            checkShards.shards.count(shard =>
              shard.parentShardId.contains(shardToSplit.shardId)
            ) == 2 && checkShards.shards.length == 7,
          s"${checkShards.shards.mkString("\n\t")}\n" +
            s"$checkStream1\n" +
            s"$checkStream2"
        )
      )
  })
}
