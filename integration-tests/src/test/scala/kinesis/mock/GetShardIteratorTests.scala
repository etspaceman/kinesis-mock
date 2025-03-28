package kinesis.mock

import scala.jdk.CollectionConverters.*

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class GetShardIteratorTests extends AwsFunctionalTests:

  fixture.test("It should get a shard iterator") { resources =>
    for
      shard <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(_.shards().asScala.head)
      res <- resources.kinesisClient
        .getShardIterator(
          GetShardIteratorRequest
            .builder()
            .shardId(shard.shardId())
            .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .attempt
    yield assert(
      res.isRight,
      s"$res"
    )
  }
