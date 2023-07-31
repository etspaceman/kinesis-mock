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
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StopStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should stop stream encryption")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
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
        keyId <- IO(keyIdGen.one)
        _ <- cache
          .startStreamEncryption(
            StartStreamEncryptionRequest(
              EncryptionType.KMS,
              keyId,
              Some(streamName),
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.startStreamEncryptionDuration.plus(400.millis)
        )
        _ <- cache
          .stopStreamEncryption(
            StopStreamEncryptionRequest(
              EncryptionType.KMS,
              keyId,
              Some(streamName),
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        describeReq = DescribeStreamSummaryRequest(Some(streamName), None)
        checkStream1 <- cache
          .describeStreamSummary(describeReq, context, false, Some(awsRegion))
          .rethrow
        _ <- IO.sleep(
          cacheConfig.stopStreamEncryptionDuration.plus(400.millis)
        )
        checkStream2 <- cache
          .describeStreamSummary(describeReq, context, false, Some(awsRegion))
          .rethrow
      } yield assert(
        checkStream1.streamDescriptionSummary.encryptionType.contains(
          EncryptionType.NONE
        ) &&
          checkStream1.streamDescriptionSummary.keyId.isEmpty &&
          checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
      )
  })
}
