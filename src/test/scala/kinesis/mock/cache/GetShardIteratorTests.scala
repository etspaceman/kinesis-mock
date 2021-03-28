package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class GetShardIteratorTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should get a shard iterator")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
        shard <- cache
          .listShards(
            ListShardsRequest(None, None, None, None, None, Some(streamName))
          )
          .rethrow
          .map(_.shards.head)
        res <- cache
          .getShardIterator(
            GetShardIteratorRequest(
              shard.shardId.shardId,
              ShardIteratorType.TRIM_HORIZON,
              None,
              streamName,
              None
            )
          )
          .rethrow
          .map(_.shardIterator)
      } yield assert(
        res.parse.isValid,
        s"$res"
      )
  })
}
