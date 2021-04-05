package kinesis.mock.cache

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream summary")(PropF.forAllF {
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
            .describeStreamSummary(
              DescribeStreamSummaryRequest(streamName),
              context
            )
            .rethrow
          expected = StreamDescriptionSummary(
            Some(0),
            Some(EncryptionType.NONE),
            List(ShardLevelMetrics(List.empty)),
            None,
            1,
            24,
            s"arn:aws:kinesis:${cacheConfig.awsRegion.entryName}:${cacheConfig.awsAccountId}:stream/$streamName",
            res.streamDescriptionSummary.streamCreationTimestamp,
            streamName,
            StreamStatus.CREATING
          )
        } yield assert(
          res.streamDescriptionSummary == expected,
          s"$res\n$expected"
        )
      )
  })
}
