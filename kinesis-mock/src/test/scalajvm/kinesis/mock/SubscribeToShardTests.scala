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
import scala.jdk.CollectionConverters.*

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

import cats.effect.IO
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class SubscribeToShardTests extends AwsFunctionalTests:

  fixture(shardCount = 1)
    .test("It should subscribe to a shard and receive records") { resources =>
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
        partitionKey = "subscribe-to-shard-test-pk"
        payload = "subscribe-to-shard-test-payload"
        received = new ConcurrentLinkedQueue[Record]()
        handler = SubscribeToShardResponseHandler
          .builder()
          .subscriber(new Consumer[SubscribeToShardEventStream] {
            def accept(t: SubscribeToShardEventStream): Unit = t match
              case e: SubscribeToShardEvent =>
                e.records().asScala.foreach(received.add)
              case _ => ()
          })
          .build()
        subFib <- IO(
          resources.kinesisClient.subscribeToShard(
            SubscribeToShardRequest
              .builder()
              .consumerARN(consumerArn)
              .shardId("shardId-000000000000")
              .startingPosition(
                StartingPosition
                  .builder()
                  .`type`(ShardIteratorType.LATEST)
                  .build()
              )
              .build(),
            handler
          )
        ).flatMap(cf => IO.fromCompletableFuture(IO.pure(cf))).start
        _ <- IO.sleep(2.seconds)
        _ <- resources.kinesisClient
          .putRecord(
            PutRecordRequest
              .builder()
              .streamName(resources.streamName.streamName)
              .partitionKey(partitionKey)
              .data(SdkBytes.fromUtf8String(payload))
              .build()
          )
          .toIO
        _ <- IO.sleep(3.seconds)
        _ <- subFib.cancel
        records = received.asScala.toVector
      yield
        assertEquals(records.size, 1, s"expected exactly 1 record, got: $records")
        val r = records.head
        assertEquals(r.partitionKey(), partitionKey)
        assertEquals(r.data().asUtf8String(), payload)
        assert(
          r.sequenceNumber() != null && r.sequenceNumber().nonEmpty,
          s"sequence number missing on record: $r"
        )
    }
