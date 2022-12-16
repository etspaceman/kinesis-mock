package kinesis.mock.cache

import kinesis.mock.LoggingContext

class DescribeLimitsTests extends munit.CatsEffectSuite {
  test("It should describe limits")(
    for {
      cacheConfig <- CacheConfig.read
      cache <- Cache(cacheConfig)
      res <- cache.describeLimits(LoggingContext.create, None).rethrow
    } yield assert(
      res.openShardCount == 0 &&
        res.shardLimit == cacheConfig.shardLimit &&
        res.onDemandStreamCountLimit == cacheConfig.onDemandStreamCountLimit &&
        res.onDemandStreamCount == 0,
      res
    )
  )
}
