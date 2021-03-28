package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

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
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        res <- cache
          .describeStreamSummary(DescribeStreamSummaryRequest(streamName))
          .rethrow
        expected = StreamDescriptionSummary(
          Some(0),
          Some(EncryptionType.NONE),
          List(ShardLevelMetrics(List.empty)),
          None,
          1,
          24,
          s"arn:aws:kinesis:${cacheConfig.awsRegion.entryName}:${cacheConfig.awsAccountId}:stream/${streamName}",
          res.streamDescriptionSummary.streamCreationTimestamp,
          streamName,
          StreamStatus.CREATING
        )
      } yield assert(
        res.streamDescriptionSummary == expected,
        s"$res\n$expected"
      )
  })
}
