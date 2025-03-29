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

import scala.concurrent.duration.*

import cats.effect.IO
import enumeratum.scalacheck.*
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api.*
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should delete a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig).map(x => (cacheConfig, x)))
        .use { case (cacheConfig, cache) =>
          val context = LoggingContext.create
          for
            _ <- cache
              .createStream(
                CreateStreamRequest(Some(1), None, streamName),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
            res <- cache
              .deleteStream(
                DeleteStreamRequest(Some(streamName), None, None),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            describeStreamSummaryReq = DescribeStreamSummaryRequest(
              Some(streamName),
              None
            )
            checkStream1 <- cache.describeStreamSummary(
              describeStreamSummaryReq,
              context,
              isCbor = false,
              Some(awsRegion)
            )
            _ <- IO.sleep(cacheConfig.deleteStreamDuration.plus(400.millis))
            checkStream2 <- cache.describeStreamSummary(
              describeStreamSummaryReq,
              context,
              isCbor = false,
              Some(awsRegion)
            )
          yield assert(
            checkStream1.exists(
              _.streamDescriptionSummary.streamStatus == StreamStatus.DELETING
            ) &&
              checkStream2.isLeft,
            s"$res\n$checkStream1\n$checkStream2"
          )
        }
  })
