package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._

class DescribeLimitsTests extends munit.CatsEffectSuite {
  test("It should describe limits")(
    for {
      cacheConfig <- CacheConfig.read.load[IO]
      cache <- Cache(cacheConfig)
      res <- cache.describeLimits.rethrow
    } yield assert(
      res.openShardCount == 0 && res.shardLimit == cacheConfig.shardLimit,
      res
    )
  )
}
