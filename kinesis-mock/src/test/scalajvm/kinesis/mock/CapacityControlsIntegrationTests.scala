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
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class CapacityControlsIntegrationTests extends AwsFunctionalTests:

  private def createOnDemandStream(
      resources: KinesisFunctionalTestResources,
      streamName: String,
      maxRecordSizeInKiB: Option[Int] = None,
      warmThroughputMiBps: Option[Int] = None,
      tags: Map[String, String] = Map.empty
  ): IO[Unit] =
    for
      _ <- resources.kinesisClient.createStream {
        val builder = CreateStreamRequest
          .builder()
          .streamName(streamName)
          .streamModeDetails(
            StreamModeDetails
              .builder()
              .streamMode(StreamMode.ON_DEMAND)
              .build()
          )
        maxRecordSizeInKiB.foreach(v =>
          val _ = builder.maxRecordSizeInKiB(Integer.valueOf(v))
        )
        warmThroughputMiBps.foreach(v =>
          val _ = builder.warmThroughputMiBps(Integer.valueOf(v))
        )
        if tags.nonEmpty then
          val _ = builder.tags(tags.asJava)
        builder.build()
      }.toIO
      _ <- IO.sleep(resources.cacheConfig.createStreamDuration.plus(400.millis))
    yield ()

  private def deleteStream(
      resources: KinesisFunctionalTestResources,
      streamName: String
  ): IO[Unit] =
    resources.kinesisClient
      .deleteStream(
        DeleteStreamRequest
          .builder()
          .streamName(streamName)
          .enforceConsumerDeletion(true)
          .build()
      )
      .toIO
      .void

  fixture().test(
    "It should create an on-demand stream with MaxRecordSizeInKiB, WarmThroughputMiBps, and Tags"
  ) { resources =>
    val streamName = streamNameGen.one.streamName
    val resourceCleanup = deleteStream(resources, streamName).attempt.void
    val test = for
      _ <- createOnDemandStream(
        resources,
        streamName,
        maxRecordSizeInKiB = Some(2048),
        warmThroughputMiBps = Some(10),
        tags = Map("env" -> "test")
      )
      summary <- describeStreamSummary(resources.kinesisClient, streamName)
      tags <- resources.kinesisClient
        .listTagsForStream(
          ListTagsForStreamRequest
            .builder()
            .streamName(streamName)
            .build()
        )
        .toIO
    yield
      val sds = summary.streamDescriptionSummary()
      assertEquals(
        Option(sds.maxRecordSizeInKiB()).map(_.intValue),
        Some(2048)
      )
      val wt = Option(sds.warmThroughput())
      assertEquals(wt.map(_.currentMiBps().intValue), Some(10))
      assertEquals(wt.map(_.targetMiBps().intValue), Some(10))
      val tagMap = tags.tags().asScala.map(t => t.key() -> t.value()).toMap
      assertEquals(tagMap, Map("env" -> "test"))
    test.guarantee(resourceCleanup)
  }

  fixture().test(
    "It should reflect UpdateMaxRecordSize in DescribeStreamSummary"
  ) { resources =>
    for
      pre <- describeStreamSummary(resources)
      streamArn = pre.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .updateMaxRecordSize(
          UpdateMaxRecordSizeRequest
            .builder()
            .streamARN(streamArn)
            .maxRecordSizeInKiB(Integer.valueOf(2048))
            .build()
        )
        .toIO
      summary <- describeStreamSummary(resources)
    yield assertEquals(
      Option(summary.streamDescriptionSummary().maxRecordSizeInKiB())
        .map(_.intValue),
      Some(2048)
    )
  }

  fixture().test(
    "It should reflect UpdateStreamWarmThroughput in DescribeStreamSummary"
  ) { resources =>
    val streamName = streamNameGen.one.streamName
    val resourceCleanup = deleteStream(resources, streamName).attempt.void
    val test = for
      _ <- createOnDemandStream(resources, streamName)
      arnSummary <- describeStreamSummary(resources.kinesisClient, streamName)
      streamArn = arnSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .updateStreamWarmThroughput(
          UpdateStreamWarmThroughputRequest
            .builder()
            .streamARN(streamArn)
            .warmThroughputMiBps(Integer.valueOf(20))
            .build()
        )
        .toIO
      summary <- describeStreamSummary(resources.kinesisClient, streamName)
    yield
      val wt = Option(summary.streamDescriptionSummary().warmThroughput())
      assertEquals(wt.map(_.currentMiBps().intValue), Some(20))
      assertEquals(wt.map(_.targetMiBps().intValue), Some(20))
    test.guarantee(resourceCleanup)
  }

  fixture().test(
    "It should enforce the default MaxRecordSizeInKiB on PutRecord"
  ) { resources =>
    val tooBig = new Array[Byte](2 * 1024 * 1024)
    val atLimit = new Array[Byte](1024 * 1024)
    for
      rejected <- resources.kinesisClient
        .putRecord(
          PutRecordRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .partitionKey("pk")
            .data(SdkBytes.fromByteArray(tooBig))
            .build()
        )
        .toIO
        .attempt
      _ <- resources.kinesisClient
        .putRecord(
          PutRecordRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .partitionKey("pk")
            .data(SdkBytes.fromByteArray(atLimit))
            .build()
        )
        .toIO
    yield rejected match
      case Left(_: InvalidArgumentException) => ()
      case other                             =>
        fail(s"expected InvalidArgumentException, got: $other")
  }
