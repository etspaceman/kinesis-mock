package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class PutRecordTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should put records")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        recordRequests <- IO(
          putRecordRequestArb.arbitrary
            .take(5)
            .toList
            .map(_.copy(streamName = streamName))
        )
        _ <- recordRequests.traverse(req => cache.putRecord(req).rethrow)
        shard <- cache
          .listShards(
            ListShardsRequest(None, None, None, None, None, Some(streamName))
          )
          .rethrow
          .map(_.shards.head)
        shardIterator <- cache
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
        res <- cache.getRecords(GetRecordsRequest(None, shardIterator)).rethrow
      } yield assert(
        res.records.length == 5 && res.records.forall(rec =>
          recordRequests.exists(req =>
            req.data.sameElements(rec.data)
              && req.partitionKey == rec.partitionKey
          )
        ),
        s"${res.records}\n${recordRequests}"
      )
  })
}
