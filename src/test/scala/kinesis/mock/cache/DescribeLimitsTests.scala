package kinesis.mock
package cache

import cats.syntax.all._

import kinesis.mock.LoggingContext

class DescribeLimitsTests extends KinesisMockSuite {
  test("It should describe limits")(
    for {
      cacheConfig <- CacheConfig.read
      cache <- Cache(cacheConfig)
      res <- cache.describeLimits(LoggingContext.create).rethrow
    } yield assert(
      res.openShardCount == 0 && res.shardLimit == cacheConfig.shardLimit,
      res
    )
  )
}
