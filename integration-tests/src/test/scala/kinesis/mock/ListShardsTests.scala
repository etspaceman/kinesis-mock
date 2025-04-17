package kinesis.mock

import scala.concurrent.duration.*

import cats.effect.IO
import cats.implicits.*
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class ListShardsTests extends AwsFunctionalTests:

  fixture.test("It should list shards") { resources =>
    for
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
        resources.cacheConfig.updateShardCountDuration.plus(400.millis)
      )
      res <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
    yield assert(
      res.shards().size() == 4,
      s"$res"
    )
  }

  fixture.test("It should list shards for initialized streams") { resources =>
    for res <- initializedStreams.map { case (name, _) =>
        resources.defaultRegionKinesisClient
          .listShards(
            ListShardsRequest
              .builder()
              .streamName(name)
              .build()
          )
          .toIO
          .map(name -> _.shards())
      }.parSequence
    yield assert(
      res.map { case (name, shards) =>
        name -> shards.size()
      } == initializedStreams,
      s"$res"
    )
  }
