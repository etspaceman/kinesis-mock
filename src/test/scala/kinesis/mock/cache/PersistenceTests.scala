/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            .map(_.copy(streamName = Some(streamName), streamArn = None))
        )
        _ <- recordRequests.traverse(req =>
          cache.putRecord(req, context, false, Some(awsRegion)).rethrow
        )
        _ <- cache.persistToDisk(context)
        newCache <- Cache.loadFromFile(cacheConfig)
        shard <- newCache
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
        shardIterator <- newCache
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
        res <- newCache
          .getRecords(
            GetRecordsRequest(None, shardIterator, None),
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
