package kinesis.mock.cache

import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream")(PropF.forAllF {
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
            CreateStreamRequest(Some(1), streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        res <- cache
          .describeStream(
            DescribeStreamRequest(None, None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        shardSummary <- cache
          .listShards(
            ListShardsRequest(
              None,
              None,
              None,
              None,
              None,
              Some(streamName)
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(x => x.shards)
        expected = StreamDescription(
          Some(EncryptionType.NONE),
          Vector(ShardLevelMetrics(Vector.empty)),
          false,
          None,
          24,
          shardSummary,
          StreamArn(awsRegion, streamName, cacheConfig.awsAccountId),
          res.streamDescription.streamCreationTimestamp,
          streamName,
          StreamStatus.CREATING
        )
      } yield assert(
        res.streamDescription == expected,
        s"$res\n$expected"
      )
  })
}
