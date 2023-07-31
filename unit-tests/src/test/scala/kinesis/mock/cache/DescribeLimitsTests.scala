/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock.cache

import cats.effect.IO

import kinesis.mock.LoggingContext

class DescribeLimitsTests extends munit.CatsEffectSuite {
  test("It should describe limits")(
    for {
      cacheConfig <- CacheConfig.read.load[IO]
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
