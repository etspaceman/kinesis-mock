package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class CreateStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should create a stream")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache.describeStreamSummary(describeStreamSummaryReq)
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        checkStream2 <- cache.describeStreamSummary(describeStreamSummaryReq)
      } yield assert(
        checkStream1.exists(
          _.streamDescriptionSummary.streamStatus == StreamStatus.CREATING
        ) &&
          checkStream2.exists(
            _.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
          )
      )
  })
}
