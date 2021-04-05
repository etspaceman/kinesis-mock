package kinesis.mock

import scala.concurrent.duration._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class ListShardsTests extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should list shards") { resources =>
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
      res <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
    } yield assert(
      res.shards().size() == 3,
      s"$res"
    )
  }
}
