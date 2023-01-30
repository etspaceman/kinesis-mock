package kinesis.mock
package cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
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
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(Some(1), None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        req <- IO(
          PutRecordsRequest(
            putRecordsRequestEntryArb.arbitrary
              .take(5)
              .toVector,
            Some(streamName),
            None
          )
        )
        _ <- cache.putRecords(req, context, false, Some(awsRegion)).rethrow
        shard <- cache
          .listShards(
            ListShardsRequest(
              None,
              None,
              None,
              None,
              None,
              Some(streamName),
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(_.shards.head)
        shardIterator <- cache
          .getShardIterator(
            GetShardIteratorRequest(
              shard.shardId,
              ShardIteratorType.TRIM_HORIZON,
              None,
              Some(streamName),
              None,
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(_.shardIterator)
        res <- cache
          .getRecords(
            GetRecordsRequest(None, shardIterator, None),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(
        res.records.length == 5 && res.records.toVector.map(
          PutRecordResults.fromKinesisRecord
        ) === req.records.map(PutRecordResults.fromPutRecordsRequestEntry),
        s"${res.records}\n$req"
      )
  })
}
