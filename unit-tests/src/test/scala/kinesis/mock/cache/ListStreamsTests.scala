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
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.AwsRegion
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests extends munit.CatsEffectSuite {
  test("It should list streams")(PropF.forAllF { (awsRegion: AwsRegion) =>
    CacheConfig.read
      .resource[IO]
      .flatMap(cacheConfig => Cache(cacheConfig))
      .use { case cache =>
        val context = LoggingContext.create
        for {
          streamNames <- IO(
            Gen
              .listOfN(5, streamNameArbitrary.arbitrary)
              .suchThat(streamNames =>
                streamNames
                  .groupBy(identity)
                  .collect { case (_, x) if x.length > 1 => x }
                  .isEmpty
              )
              .one
              .sorted
          )
          _ <- streamNames.traverse(streamName =>
            cache
              .createStream(
                CreateStreamRequest(Some(1), None, streamName),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
          )
          res <- cache
            .listStreams(
              ListStreamsRequest(None, None),
              context,
              isCbor = false,
              Some(awsRegion)
            )
            .rethrow
        } yield assert(
          res.streamNames == streamNames,
          s"${res.streamNames}\n${streamNames}"
        )
      }
  })
}
