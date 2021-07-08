package kinesis.mock

import kinesis.mock.syntax.javaFuture._

class DescribeLimitsTest extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should describe limits") { resources =>
    for {
      res <- resources.kinesisClient
        .describeLimits()
        .toIO
    } yield assert(
      res.openShardCount() == (initializedStreams
        .map(_._2)
        .sum + genStreamShardCount) &&
        res.shardLimit() == resources.cacheConfig.shardLimit,
      s"$res"
    )
  }
}
