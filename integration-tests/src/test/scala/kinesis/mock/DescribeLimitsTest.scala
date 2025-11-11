package kinesis.mock

import kinesis.mock.syntax.javaFuture.*

class DescribeLimitsTest extends AwsFunctionalTests:

  fixture().test("It should describe limits") { resources =>
    for res <- resources.kinesisClient
        .describeLimits()
        .toIO
    yield assert(
      res.openShardCount() == defaultShardCount &&
        res.shardLimit() == resources.cacheConfig.shardLimit,
      s"$res"
    )
  }

  fixture().test("It should describe limits for the initialized streams") {
    resources =>
      for res <- resources.defaultRegionKinesisClient
          .describeLimits()
          .toIO
      yield assert(
        res.openShardCount() == initializedStreams.map(_._2).sum &&
          res.shardLimit() == resources.cacheConfig.shardLimit,
        s"$res"
      )
  }
