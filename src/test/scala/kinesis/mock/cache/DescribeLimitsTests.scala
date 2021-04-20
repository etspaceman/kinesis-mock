package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._

import kinesis.mock.LoggingContext
import cats.effect.Resource

class DescribeLimitsTests extends munit.CatsEffectSuite {
  test("It should describe limits")(
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        res <- cache.describeLimits(LoggingContext.create).rethrow
      } yield assert(
        res.openShardCount == 0 && res.shardLimit == cacheConfig.shardLimit,
        res
      )
    )
  )
}
