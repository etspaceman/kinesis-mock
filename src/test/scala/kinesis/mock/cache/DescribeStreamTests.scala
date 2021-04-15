package kinesis.mock.cache

import cats.effect.{Blocker, IO}
import cats.syntax.all._
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
      streamName: StreamName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          res <- cache
            .describeStream(
              DescribeStreamRequest(None, None, streamName),
              context
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
              context
            )
            .rethrow
            .map(x => x.shards)
          expected = StreamDescription(
            Some(EncryptionType.NONE),
            List(ShardLevelMetrics(List.empty)),
            false,
            None,
            24,
            shardSummary,
            s"arn:aws:kinesis:${cacheConfig.awsRegion.entryName}:${cacheConfig.awsAccountId}:stream/$streamName",
            res.streamDescription.streamCreationTimestamp,
            streamName,
            StreamStatus.CREATING
          )
        } yield assert(
          res.streamDescription == expected,
          s"$res\n$expected"
        )
      )
  })
}
