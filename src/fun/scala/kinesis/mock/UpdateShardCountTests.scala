package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class UpdateShardCountTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should update the shard count") { case resources =>
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
    } yield assert(
      openShards.length == 2,
      s"$openShards"
    )
  }
}
