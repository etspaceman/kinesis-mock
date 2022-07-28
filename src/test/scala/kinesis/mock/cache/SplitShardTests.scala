package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
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
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(streamName, 5),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        listShardsReq = ListShardsRequest(
          None,
          None,
          None,
          None,
          None,
          Some(streamName)
        )
        shardToSplit <- cache
          .listShards(listShardsReq, context, false, Some(awsRegion))
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
            false,
            Some(awsRegion)
          )
          .rethrow
        describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache
          .describeStreamSummary(
            describeStreamSummaryReq,
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.splitShardDuration.plus(500.millis))
        checkStream2 <- cache
          .describeStreamSummary(
            describeStreamSummaryReq,
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        checkShards <- cache
          .listShards(listShardsReq, context, false, Some(awsRegion))
          .rethrow
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
  })
}
