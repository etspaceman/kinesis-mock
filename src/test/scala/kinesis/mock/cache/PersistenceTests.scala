package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import com.github.f4b6a3.uuid.UuidCreator
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class PersistenceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  def persistConfig(uuid: String) = PersistConfig(
    true,
    true,
    "testing/data",
    s"test-$uuid",
    0.millis
  )

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  override def afterAll(): Unit = os.remove.all(persistConfig("").osPath)

  test("It should persist data and re-load it from disk")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        uuid <- IO(UuidCreator.getTimeBased().toString)
        cacheConfig <- CacheConfig.read
          .map(_.copy(persistConfig = persistConfig(uuid)))
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
        recordRequests <- IO(
          putRecordRequestArb.arbitrary
            .take(5)
            .toVector
            .map(_.copy(streamName = streamName))
        )
        _ <- recordRequests.traverse(req =>
          cache.putRecord(req, context, false, Some(awsRegion)).rethrow
        )
        _ <- cache.persistToDisk(context)
        newCache <- Cache.loadFromFile(cacheConfig)
        shard <- newCache
          .listShards(
            ListShardsRequest(None, None, None, None, None, Some(streamName)),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(_.shards.head)
        shardIterator <- newCache
          .getShardIterator(
            GetShardIteratorRequest(
              shard.shardId,
              ShardIteratorType.TRIM_HORIZON,
              None,
              streamName,
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(_.shardIterator)
        res <- newCache
          .getRecords(
            GetRecordsRequest(None, shardIterator),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(
        res.records.length == 5 && res.records.forall(rec =>
          recordRequests.exists(req =>
            req.data.sameElements(rec.data)
              && req.partitionKey == rec.partitionKey
          )
        ),
        s"${res.records}\n$recordRequests"
      )
  })
}
