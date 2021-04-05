package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class MergeShardsTests extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should merge shards") { resources =>
    for {
      _ <- resources.kinesisClient
        .updateShardCount(
          UpdateShardCountRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .scalingType(ScalingType.UNIFORM_SCALING)
            .targetShardCount(2)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.updateShardCountDuration.plus(100.millis)
      )
      openShards <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(
          _.shards().asScala.toList.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
      _ <- resources.kinesisClient
        .mergeShards(
          MergeShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .adjacentShardToMerge(openShards.last.shardId())
            .shardToMerge(openShards.head.shardId())
            .build()
        )
        .toIO
      _ <- IO.sleep(resources.cacheConfig.mergeShardsDuration.plus(100.millis))
      openShards2 <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(
          _.shards().asScala.toList.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
    } yield assert(
      openShards2.length == 1,
      s"$openShards\n$openShards2"
    )
  }
}
