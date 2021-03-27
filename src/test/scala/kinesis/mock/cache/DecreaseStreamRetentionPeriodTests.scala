package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should decrease the stream retention period")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        _ <- cache
          .increaseStreamRetention(
            IncreaseStreamRetentionRequest(48, streamName)
          )
          .rethrow
        res <- cache.decreaseStreamRetention(
          DecreaseStreamRetentionRequest(24, streamName)
        )
      } yield assert(res.isRight)
  })
}
