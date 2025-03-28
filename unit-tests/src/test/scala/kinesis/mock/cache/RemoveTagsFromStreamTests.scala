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

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models._

class RemoveTagsFromStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should remove tags")(PropF.forAllF {
    (
        streamName: StreamName,
        tags: Tags,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig))
        .use { case cache =>
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
            _ <- cache
              .addTagsToStream(
                AddTagsToStreamRequest(Some(streamName), None, tags),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            _ <- cache.removeTagsFromStream(
              RemoveTagsFromStreamRequest(
                Some(streamName),
                None,
                tags.tags.keys.toVector
              ),
              context,
              isCbor = false,
              Some(awsRegion)
            )
            res <- cache
              .listTagsForStream(
                ListTagsForStreamRequest(None, None, Some(streamName), None),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
          } yield assert(res.tags.tags.isEmpty)
        }
  })
}
