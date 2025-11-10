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

package kinesis.mock
package cache

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import enumeratum.scalacheck.*
import fs2.io.file.Files
import org.scalacheck.Arbitrary
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api.*
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*
import kinesis.mock.syntax.scalacheck.*

class PersistenceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:

  def persistConfig(uuid: String): PersistConfig = PersistConfig(
    loadIfExists = true,
    shouldPersist = true,
    "testing/data",
    s"test-$uuid",
    0.millis
  )

  val fixture: SyncIO[FunFixture[Unit]] =
    ResourceFunFixture(
      Resource.onFinalize(Files[IO].deleteRecursively(persistConfig("").osPath))
    )

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  fixture.test("It should persist data and re-load it from disk")(_ =>
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion
      ) =>
        CacheConfig.read
          .resource[IO]
          .both(Resource.eval(Utils.randomUUIDString))
          .map { case (cacheConfig, uuid) =>
            cacheConfig.copy(persistConfig = persistConfig(uuid))
          }
          .flatMap(cacheConfig => Cache(cacheConfig).map(x => (cacheConfig, x)))
          .evalMap { case (cacheConfig, cache) =>
            for
              context <- LoggingContext.create
              _ <- cache
                .createStream(
                  CreateStreamRequest(Some(1), None, streamName),
                  context,
                  isCbor = false,
                  Some(awsRegion)
                )
                .rethrow
              _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
              recordRequests <- IO(
                Arbitrary
                  .arbitrary[PutRecordRequest]
                  .take(5)
                  .toVector
                  .map(_.copy(streamName = Some(streamName), streamArn = None))
              )
              _ <- recordRequests.traverse(req =>
                cache
                  .putRecord(req, context, isCbor = false, Some(awsRegion))
                  .rethrow
              )
              _ <- cache.persistToDisk(context)
            yield (cacheConfig, recordRequests)
          }
          .flatMap { case (cacheConfig, recordRequests) =>
            Cache
              .loadFromFile(cacheConfig)
              .map(newCache => (newCache, recordRequests))
          }
          .use { case (newCache, recordRequests) =>
            for
              context <- LoggingContext.create
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
                  isCbor = false,
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
                  isCbor = false,
                  Some(awsRegion)
                )
                .rethrow
                .map(_.shardIterator)
              res <- newCache
                .getRecords(
                  GetRecordsRequest(None, shardIterator, None),
                  context,
                  isCbor = false,
                  Some(awsRegion)
                )
                .rethrow
            yield assert(
              res.records.length == 5 && res.records.forall(rec =>
                recordRequests.exists(req =>
                  req.data.sameElements(rec.data)
                    && req.partitionKey == rec.partitionKey
                )
              ),
              s"${res.records}\n$recordRequests"
            )
          }
    }
  )

  test("It should make a root path") {
    Utils.randomUUIDString.map { id =>
      val config =
        val orig = persistConfig(id)
        orig.copy(path = s"/${orig.path}")

      assertEquals(config.osPath.absolute.toString, config.path)
    }
  }
