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

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
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
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig).map(x => (cacheConfig, x)))
        .use { case (cacheConfig, cache) =>
          val context = LoggingContext.create
          for {
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
              putRecordRequestArb.arbitrary
                .take(5)
                .toVector
                .map(_.copy(streamName = Some(streamName), streamArn = None))
            )
            _ <- recordRequests.traverse(req =>
              cache
                .putRecord(req, context, isCbor = false, Some(awsRegion))
                .rethrow
            )
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
                isCbor = false,
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
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
              .map(_.shardIterator)
            res <- cache
              .getRecords(
                GetRecordsRequest(None, shardIterator, None),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
          } yield assert(
            res.records.length == 5 && res.records.toVector.map(
              PutRecordResults.fromKinesisRecord
            ) === recordRequests.map(PutRecordResults.fromPutRecordRequest),
            s"${res.records}\n$recordRequests"
          )
        }
  })
}
