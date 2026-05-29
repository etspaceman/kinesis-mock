/*
 * Copyright 2021-2026 io.github.etspaceman
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
import kinesis.mock.api.*
import kinesis.mock.models.*

class MultiAccountTests extends munit.CatsEffectSuite:

  test("streams created under different accounts are isolated"):
    CacheConfig.read
      .resource[IO]
      .flatMap(cacheConfig => Cache(cacheConfig))
      .use { cache =>
        val acct1 = AwsAccountId("111111111111")
        val acct2 = AwsAccountId("222222222222")
        val name = StreamName("shared-name")
        val req = CreateStreamRequest(Some(1), None, name, None, None, None)
        val listReq = ListStreamsRequest(None, None)

        for
          lc <- LoggingContext.create
          _ <- cache
            .createStream(req, lc, isCbor = false, None, Some(acct1))
            .rethrow
          _ <- cache
            .createStream(req, lc, isCbor = false, None, Some(acct2))
            .rethrow
          l1 <- cache
            .listStreams(listReq, lc, isCbor = false, None, Some(acct1))
            .rethrow
          l2 <- cache
            .listStreams(listReq, lc, isCbor = false, None, Some(acct2))
            .rethrow
        yield
          assertEquals(l1.streamNames.size, 1, "acct1 sees its own stream")
          assertEquals(l2.streamNames.size, 1, "acct2 sees its own stream")
          assertEquals(l1.streamNames.head, name)
          assertEquals(l2.streamNames.head, name)
      }
