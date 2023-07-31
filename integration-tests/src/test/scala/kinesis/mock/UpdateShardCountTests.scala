package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class UpdateShardCountTests extends AwsFunctionalTests {

  fixture.test("It should update the shard count") { resources =>
    for {
      res <- resources.kinesisClient
        .updateShardCount(
          UpdateShardCountRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .scalingType(ScalingType.UNIFORM_SCALING)
            .targetShardCount(6)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.updateShardCountDuration.plus(400.millis)
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
          _.shards().asScala.toVector.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
    } yield assert(
      openShards.length == 6 &&
        res.currentShardCount() == 3 &&
        res.targetShardCount() == 6 &&
        res.streamName() == resources.streamName.streamName,
      s"$openShards\n${res.currentShardCount()}\n${res.targetShardCount()}\n${res.streamName()}}"
    )
  }
}
