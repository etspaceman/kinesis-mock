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

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class TagResourceIntegrationTests extends AwsFunctionalTests:

  fixture().test(
    "It should tag, list, and untag a stream by ARN"
  ) { resources =>
    for
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .tagResource(
          TagResourceRequest
            .builder()
            .resourceARN(streamArn)
            .tags(Map("k1" -> "v1", "k2" -> "v2").asJava)
            .build()
        )
        .toIO
      afterTag <- resources.kinesisClient
        .listTagsForResource(
          ListTagsForResourceRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
      _ <- resources.kinesisClient
        .untagResource(
          UntagResourceRequest
            .builder()
            .resourceARN(streamArn)
            .tagKeys(List("k1").asJava)
            .build()
        )
        .toIO
      afterUntag <- resources.kinesisClient
        .listTagsForResource(
          ListTagsForResourceRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
    yield
      val tagged = afterTag.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(tagged, Map("k1" -> "v1", "k2" -> "v2"))
      val untagged = afterUntag.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(untagged, Map("k2" -> "v2"))
  }

  fixture().test(
    "It should expose AddTagsToStream tags via ListTagsForResource (unified storage)"
  ) { resources =>
    for
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .addTagsToStream(
          AddTagsToStreamRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .tags(Map("k1" -> "v1").asJava)
            .build()
        )
        .toIO
      viaResource <- resources.kinesisClient
        .listTagsForResource(
          ListTagsForResourceRequest
            .builder()
            .resourceARN(streamArn)
            .build()
        )
        .toIO
      _ <- resources.kinesisClient
        .tagResource(
          TagResourceRequest
            .builder()
            .resourceARN(streamArn)
            .tags(Map("k2" -> "v2").asJava)
            .build()
        )
        .toIO
      viaStream <- listTagsForStream(resources)
    yield
      val viaResourceMap =
        viaResource.tags().asScala.map(t => t.key() -> t.value()).toMap
      assert(
        viaResourceMap.get("k1").contains("v1"),
        s"ListTagsForResource missing k1=v1: $viaResourceMap"
      )
      val viaStreamMap =
        viaStream.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(viaStreamMap, Map("k1" -> "v1", "k2" -> "v2"))
  }

  fixture().test(
    "It should tag, list, and untag a consumer by ARN"
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
        .tagResource(
          TagResourceRequest
            .builder()
            .resourceARN(consumerArn)
            .tags(Map("k1" -> "v1", "k2" -> "v2").asJava)
            .build()
        )
        .toIO
      afterTag <- resources.kinesisClient
        .listTagsForResource(
          ListTagsForResourceRequest
            .builder()
            .resourceARN(consumerArn)
            .build()
        )
        .toIO
      _ <- resources.kinesisClient
        .untagResource(
          UntagResourceRequest
            .builder()
            .resourceARN(consumerArn)
            .tagKeys(List("k1").asJava)
            .build()
        )
        .toIO
      afterUntag <- resources.kinesisClient
        .listTagsForResource(
          ListTagsForResourceRequest
            .builder()
            .resourceARN(consumerArn)
            .build()
        )
        .toIO
    yield
      val tagged = afterTag.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(tagged, Map("k1" -> "v1", "k2" -> "v2"))
      val untagged =
        afterUntag.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(untagged, Map("k2" -> "v2"))
  }
