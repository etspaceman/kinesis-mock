package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class UpdateShardCountTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should update the shard count")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(CreateStreamRequest(5, streamName), context, false)
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
        res <- cache
          .updateShardCount(
            UpdateShardCountRequest(
              ScalingType.UNIFORM_SCALING,
              streamName,
              10
            ),
            context,
            false
          )
          .rethrow
        describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache
          .describeStreamSummary(describeStreamSummaryReq, context, false)
          .rethrow
        _ <- IO.sleep(cacheConfig.updateShardCountDuration.plus(200.millis))
        checkStream2 <- cache
          .describeStreamSummary(describeStreamSummaryReq, context, false)
          .rethrow
        checkShards <- cache
          .listShards(
            ListShardsRequest(None, None, None, None, None, Some(streamName)),
            context,
            false
          )
          .rethrow
      } yield assert(
        checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
          checkShards.shards.count(!_.isOpen) == 5 &&
          checkShards.shards.count(_.isOpen) == 10 &&
          checkShards.shards.length == 15 &&
          res.currentShardCount == 5 &&
          res.streamName == streamName &&
          res.targetShardCount == 10,
        s"${checkShards.shards.mkString("\n\t")}\n" +
          s"$checkStream1\n" +
          s"$checkStream2"
      )
  })
}
