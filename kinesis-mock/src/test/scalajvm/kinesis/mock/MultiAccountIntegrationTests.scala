/*
 * Copyright 2021-2026 io.github.etspaceman
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

import scala.concurrent.duration.*

import java.net.URI

import cats.effect.{IO, Resource}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.cache.CacheConfig
import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class MultiAccountIntegrationTests extends AwsFunctionalTests:

  // A client that signs with `accountId` as its access key id. With the
  // 12-digit-account-id convention, this selects that account on the shared
  // instance.
  private def clientForAccount(
      accountId: String,
      protocol: String,
      port: Int
  ): Resource[IO, KinesisAsyncClient] =
    Resource.fromAutoCloseable(
      IO(
        KinesisAsyncClient
          .builder()
          .httpClient(nettyClient)
          .region(Region.US_EAST_1)
          .credentialsProvider(AwsCreds(accountId, "mockKinesisSecretKey"))
          .endpointOverride(URI.create(s"$protocol://localhost:$port"))
          .build()
      )
    )

  private def streamResource(
      client: KinesisAsyncClient,
      streamName: String,
      cacheConfig: CacheConfig
  ): Resource[IO, Unit] =
    Resource.make(
      client
        .createStream(
          CreateStreamRequest
            .builder()
            .streamName(streamName)
            .shardCount(1)
            .build()
        )
        .toIO
        .void *> IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
    )(_ =>
      client
        .deleteStream(
          DeleteStreamRequest.builder().streamName(streamName).build()
        )
        .toIO
        .void *> IO.sleep(cacheConfig.deleteStreamDuration.plus(400.millis))
    )

  test(
    "a single instance namespaces streams by the 12-digit account id in the access key id"
  ) {
    val acct1 = "111111111111"
    val acct2 = "222222222222"
    val streamName = streamNameGen.one.streamName

    (for
      testConfig <- FunctionalTestConfig.read.resource[IO]
      protocol = if testConfig.servicePort == 4568 then "http" else "https"
      cacheConfig <- CacheConfig.read.resource[IO]
      c1 <- clientForAccount(acct1, protocol, testConfig.servicePort)
      c2 <- clientForAccount(acct2, protocol, testConfig.servicePort)
      // Both accounts create a stream with the SAME name. On a single-account
      // instance the second create would fail with ResourceInUseException; here
      // both succeed because the streams live under different accounts.
      _ <- streamResource(c1, streamName, cacheConfig)
      _ <- streamResource(c2, streamName, cacheConfig)
    yield (c1, c2)).use { case (c1, c2) =>
      for
        s1 <- describeStreamSummary(c1, streamName)
        s2 <- describeStreamSummary(c2, streamName)
        arn1 = s1.streamDescriptionSummary().streamARN()
        arn2 = s2.streamDescriptionSummary().streamARN()
      yield
        assert(arn1.contains(s":$acct1:stream/$streamName"), arn1)
        assert(arn2.contains(s":$acct2:stream/$streamName"), arn2)
        assertNotEquals(arn1, arn2)
    }
  }
