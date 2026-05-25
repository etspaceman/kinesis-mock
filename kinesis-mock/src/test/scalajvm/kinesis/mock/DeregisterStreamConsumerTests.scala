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

class DeregisterStreamConsumerTests extends AwsFunctionalTests:

  fixture().test("It should deregister a stream consumer") { resources =>
    for
      consumerName <- IO(consumerNameGen.one.consumerName)
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
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
      _ <- resources.kinesisClient
        .deregisterStreamConsumer(
          DeregisterStreamConsumerRequest
            .builder()
            .streamARN(streamSummary.streamDescriptionSummary().streamARN())
            .consumerName(consumerName)
            .build()
        )
        .toIO
      check1 <- describeStreamConsumer(resources, consumerName, streamArn)
      _ <- IO.sleep(
        resources.cacheConfig.deregisterStreamConsumerDuration.plus(400.millis)
      )
      check2 <- describeStreamConsumer(
        resources,
        consumerName,
        streamArn
      ).attempt
    yield assert(
      check1
        .consumerDescription()
        .consumerStatus() == ConsumerStatus.DELETING &&
        check2.isLeft,
      s"$check1\n$check2"
    )
  }
