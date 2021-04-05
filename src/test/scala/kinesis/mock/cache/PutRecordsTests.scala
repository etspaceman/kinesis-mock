package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class PutRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should put records in batch")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(100.millis))
          req <- IO(
            PutRecordsRequest(
              putRecordsRequestEntryArb.arbitrary
                .take(5)
                .toList,
              streamName
            )
          )
          _ <- cache.putRecords(req, context).rethrow
          shard <- cache
            .listShards(
              ListShardsRequest(None, None, None, None, None, Some(streamName)),
              context
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
              ),
              context
            )
            .rethrow
            .map(_.shardIterator)
          res <- cache
            .getRecords(GetRecordsRequest(None, shardIterator), context)
            .rethrow
        } yield assert(
          res.records.length == 5 && res.records.forall(rec =>
            req.records.exists(req =>
              req.data.sameElements(rec.data)
                && req.partitionKey == rec.partitionKey
            )
          ),
          s"${res.records}\n$req"
        )
      )
  })
}
