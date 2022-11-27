package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
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
            CreateStreamRequest(Some(5), streamName),
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
        newStartingHashKey = shardToSplit.hashKeyRange.endingHashKey / BigInt(2)
        _ <- cache
          .splitShard(
            SplitShardRequest(
              newStartingHashKey.toString(),
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
        newShards = checkShards.shards.filter(shard =>
          shard.parentShardId.contains(shardToSplit.shardId)
        )
      } yield assert(
        checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
          checkShards.shards.count(!_.isOpen) == 1 &&
          newShards.size == 2 && checkShards.shards.length == 7 && (
            (newShards(0).hashKeyRange == HashKeyRange(
              startingHashKey = shardToSplit.hashKeyRange.startingHashKey,
              endingHashKey = newStartingHashKey - BigInt(1)
            ) && newShards(1).hashKeyRange == HashKeyRange(
              startingHashKey = newStartingHashKey,
              endingHashKey = shardToSplit.hashKeyRange.endingHashKey
            )) || (newShards(0).hashKeyRange == HashKeyRange(
              startingHashKey = newStartingHashKey,
              endingHashKey = shardToSplit.hashKeyRange.endingHashKey
            ) && newShards(1).hashKeyRange == HashKeyRange(
              startingHashKey = shardToSplit.hashKeyRange.startingHashKey,
              endingHashKey = newStartingHashKey - BigInt(1)
            ))
          ),
        s"${checkShards.shards.mkString("\n\t")}\n" +
          s"$checkStream1\n" +
          s"$checkStream2"
      )
  })
}
