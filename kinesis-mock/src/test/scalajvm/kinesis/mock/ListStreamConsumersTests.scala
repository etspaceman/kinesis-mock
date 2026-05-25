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

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.syntax.all.*
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.models.ConsumerArn
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class ListStreamConsumersTests extends AwsFunctionalTests:

  fixture().test("It should list stream consumers") { resources =>
    for
      consumerNames <- IO(
        consumerNameGen.take(3).toList.sorted.map(_.consumerName)
      )
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      registerRes <- consumerNames.traverse(consumerName =>
        resources.kinesisClient
          .registerStreamConsumer(
            RegisterStreamConsumerRequest
              .builder()
              .streamARN(streamArn)
              .consumerName(consumerName)
              .build()
          )
          .toIO
      )
      res <- resources.kinesisClient
        .listStreamConsumers(
          ListStreamConsumersRequest.builder().streamARN(streamArn).build()
        )
        .toIO
      resultConsumers <- res.consumers.asScala.toList.traverse(x =>
        IO.fromEither(
          ConsumerArn.fromArn(x.consumerARN()).leftMap(new RuntimeException(_))
        ).map(consumerArn =>
          models.ConsumerSummary(
            consumerArn,
            x.consumerCreationTimestamp(),
            models.ConsumerName(x.consumerName()),
            models.ConsumerStatus.withName(x.consumerStatusAsString())
          )
        )
      )
      registerResultConsumers <- registerRes
        .map(_.consumer())
        .traverse(x =>
          IO.fromEither(
            ConsumerArn
              .fromArn(x.consumerARN())
              .leftMap(new RuntimeException(_))
          ).map(consumerArn =>
            models.ConsumerSummary(
              consumerArn,
              x.consumerCreationTimestamp(),
              models.ConsumerName(x.consumerName()),
              models.ConsumerStatus.withName(x.consumerStatusAsString())
            )
          )
        )
    yield assert(
      resultConsumers === registerResultConsumers,
      s"$res\n$registerRes"
    )
  }
