package kinesis.mock

import kinesis.mock.syntax.javaFuture._

class DescribeLimitsTest extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should describe limits") { case resources =>
    for {
      res <- resources.kinesisClient
        .describeLimits()
        .toIO
    } yield assert(
      res.openShardCount() == 1 &&
        res.shardLimit() == resources.cacheConfig.shardLimit,
      s"$res"
    )
  }
}
