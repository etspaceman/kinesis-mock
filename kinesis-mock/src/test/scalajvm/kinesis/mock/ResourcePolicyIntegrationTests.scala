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

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class ResourcePolicyIntegrationTests extends AwsFunctionalTests:

  private val policy =
    """{"Version":"2012-10-17","Statement":[{"Sid":"a","Effect":"Allow","Principal":"*","Action":"kinesis:DescribeStream","Resource":"*"}]}"""

  fixture().test(
    "It should put, get, and delete a resource policy on a stream ARN"
  ) { resources =>
    for
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .putResourcePolicy(
          PutResourcePolicyRequest
            .builder()
            .resourceARN(streamArn)
            .policy(policy)
            .build()
        )
        .toIO
      got <- resources.kinesisClient
        .getResourcePolicy(
          GetResourcePolicyRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
      _ <- resources.kinesisClient
        .deleteResourcePolicy(
          DeleteResourcePolicyRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
      afterDelete <- resources.kinesisClient
        .getResourcePolicy(
          GetResourcePolicyRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
        .attempt
    yield
      assertEquals(got.policy(), policy)
      afterDelete match
        case Left(_: ResourceNotFoundException) => ()
        case other                              =>
          fail(s"expected ResourceNotFoundException after delete, got: $other")
  }

  fixture().test(
    "It should put, get, and delete a resource policy on a consumer ARN"
  ) { resources =>
    for
      consumerName <- IO(consumerNameGen.one.consumerName)
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      registered <- resources.kinesisClient
        .registerStreamConsumer(
          RegisterStreamConsumerRequest
            .builder()
            .streamARN(streamArn)
            .consumerName(consumerName)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.registerStreamConsumerDuration.plus(400.millis)
      )
      consumerArn = registered.consumer().consumerARN()
      _ <- resources.kinesisClient
        .putResourcePolicy(
          PutResourcePolicyRequest
            .builder()
            .resourceARN(consumerArn)
            .policy(policy)
            .build()
        )
        .toIO
      got <- resources.kinesisClient
        .getResourcePolicy(
          GetResourcePolicyRequest
            .builder()
            .resourceARN(consumerArn)
            .build()
        )
        .toIO
      _ <- resources.kinesisClient
        .deleteResourcePolicy(
          DeleteResourcePolicyRequest
            .builder()
            .resourceARN(consumerArn)
            .build()
        )
        .toIO
      afterDelete <- resources.kinesisClient
        .getResourcePolicy(
          GetResourcePolicyRequest
            .builder()
            .resourceARN(consumerArn)
            .build()
        )
        .toIO
        .attempt
    yield
      assertEquals(got.policy(), policy)
      afterDelete match
        case Left(_: ResourceNotFoundException) => ()
        case other                              =>
          fail(s"expected ResourceNotFoundException after delete, got: $other")
  }
