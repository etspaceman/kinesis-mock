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

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class DescribeStreamConsumerTests extends AwsFunctionalTests:

  fixture().test("It should describe a stream consumer") { resources =>
    for
      consumerName <- IO(consumerNameGen.one.consumerName)
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      registerRes <- resources.kinesisClient
        .registerStreamConsumer(
          RegisterStreamConsumerRequest
            .builder()
            .streamARN(streamArn)
            .consumerName(consumerName)
            .build()
        )
        .toIO
      res <- describeStreamConsumer(resources, consumerName, streamArn)
    yield assert(
      res
        .consumerDescription()
        .consumerARN == registerRes.consumer().consumerARN() &&
        res
          .consumerDescription()
          .consumerName == registerRes.consumer().consumerName() &&
        res.consumerDescription().consumerCreationTimestamp == registerRes
          .consumer()
          .consumerCreationTimestamp() &&
        res
          .consumerDescription()
          .consumerStatus == registerRes.consumer().consumerStatus() &&
        res.consumerDescription().streamARN() == streamArn,
      s"$res\n$registerRes"
    )
  }
