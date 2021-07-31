package kinesis.mock
package cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._
import cats.effect.Resource

class PutRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should put records in batch")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context, false)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
          req <- IO(
            PutRecordsRequest(
              putRecordsRequestEntryArb.arbitrary
                .take(5)
                .toVector,
              streamName
            )
          )
          _ <- cache.putRecords(req, context, false).rethrow
          shard <- cache
            .listShards(
              ListShardsRequest(None, None, None, None, None, Some(streamName)),
              context,
              false
            )
            .rethrow
            .map(_.shards.head)
          shardIterator <- cache
            .getShardIterator(
              GetShardIteratorRequest(
                shard.shardId,
                ShardIteratorType.TRIM_HORIZON,
                None,
                streamName,
                None
              ),
              context,
              false
            )
            .rethrow
            .map(_.shardIterator)
          res <- cache
            .getRecords(GetRecordsRequest(None, shardIterator), context, false)
            .rethrow
        } yield assert(
          res.records.length == 5 && res.records.toVector.map(
            PutRecordResults.fromKinesisRecord
          ) === req.records.map(PutRecordResults.fromPutRecordsRequestEntry),
          s"${res.records}\n$req"
        )
      )
  })
}
