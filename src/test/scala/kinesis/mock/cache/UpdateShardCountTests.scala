package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

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
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          _ <- cache.createStream(CreateStreamRequest(5, streamName)).rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
          _ <- cache
            .updateShardCount(
              UpdateShardCountRequest(
                ScalingType.UNIFORM_SCALING,
                streamName,
                10
              )
            )
            .rethrow
          describeStreamSummaryReq = DescribeStreamSummaryRequest(streamName)
          checkStream1 <- cache
            .describeStreamSummary(describeStreamSummaryReq)
            .rethrow
          _ <- IO.sleep(cacheConfig.updateShardCountDuration.plus(50.millis))
          checkStream2 <- cache
            .describeStreamSummary(describeStreamSummaryReq)
            .rethrow
          checkShards <- cache
            .listShards(
              ListShardsRequest(None, None, None, None, None, Some(streamName))
            )
            .rethrow
        } yield assert(
          checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
            checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
            checkShards.shards.count(!_.isOpen) == 5 &&
            checkShards.shards.count(_.isOpen) == 10 &&
            checkShards.shards.length == 15,
          s"${checkShards.shards.mkString("\n\t")}\n" +
            s"$checkStream1\n" +
            s"$checkStream2"
        )
      )
  })
}
