package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should delete a stream")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        res <- cache
          .deleteStream(
            DeleteStreamRequest(streamName, None)
          )
          .rethrow
        describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache.describeStreamSummary(describeStreamSummaryReq)
        _ <- IO.sleep(cacheConfig.deleteStreamDuration.plus(10.millis))
        checkStream2 <- cache.describeStreamSummary(describeStreamSummaryReq)
      } yield assert(
        checkStream1.exists(
          _.streamDescriptionSummary.streamStatus == StreamStatus.DELETING
        ) &&
          checkStream2.isLeft,
        s"$res\n$checkStream1\n$checkStream2"
      )
  })
}
