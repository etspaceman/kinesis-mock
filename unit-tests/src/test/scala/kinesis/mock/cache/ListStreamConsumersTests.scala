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

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF
import org.scalacheck.{Gen, Test}

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should list stream consumers")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(Some(1), None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        streamArn <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(Some(streamName), None),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(_.streamDescriptionSummary.streamArn)
        consumerNames <- IO(
          Gen
            .listOfN(3, consumerNameArb.arbitrary)
            .suchThat(x =>
              x.groupBy(identity)
                .collect { case (_, y) if y.length > 1 => x }
                .isEmpty
            )
            .one
        )
        registerResults <- consumerNames.sorted.toVector.traverse(
          consumerName =>
            cache
              .registerStreamConsumer(
                RegisterStreamConsumerRequest(consumerName, streamArn),
                context,
                false
              )
              .rethrow
        )
        res <- cache
          .listStreamConsumers(
            ListStreamConsumersRequest(None, None, streamArn, None),
            context,
            false
          )
          .rethrow
      } yield assert(
        res.consumers == registerResults
          .map(_.consumer),
        s"${registerResults.map(_.consumer)}\n" +
          s"${res.consumers}"
      )
  })
}
