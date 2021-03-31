package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should decrease the stream retention period")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
          _ <- cache
            .increaseStreamRetention(
              IncreaseStreamRetentionPeriodRequest(48, streamName)
            )
            .rethrow
          _ <- cache
            .decreaseStreamRetention(
              DecreaseStreamRetentionPeriodRequest(24, streamName)
            )
            .rethrow
          res <- cache
            .describeStreamSummary(DescribeStreamSummaryRequest(streamName))
            .rethrow
        } yield assert(res.streamDescriptionSummary.retentionPeriodHours == 24)
      )
  })
}
