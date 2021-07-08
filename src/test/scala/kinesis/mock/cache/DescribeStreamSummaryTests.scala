package kinesis.mock
package cache

import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests extends KinesisMockSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream summary")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(CreateStreamRequest(1, streamName), context, false)
          .rethrow
        res <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false
          )
          .rethrow
        expected = StreamDescriptionSummary(
          Some(0),
          Some(EncryptionType.NONE),
          Vector(ShardLevelMetrics(Vector.empty)),
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
  })
}
