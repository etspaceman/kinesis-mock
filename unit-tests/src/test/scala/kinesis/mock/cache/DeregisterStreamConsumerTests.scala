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
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeregisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should deregister a stream consumer")(PropF.forAllF {
    (
        consumerName: ConsumerName,
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig).map(x => (cacheConfig, x)))
        .use { case (cacheConfig, cache) =>
          val streamArn =
            StreamArn(awsRegion, streamName, cacheConfig.awsAccountId)
          for {
            context <- LoggingContext.create
            _ <- cache
              .createStream(
                CreateStreamRequest(Some(1), None, streamName),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
            _ <- cache
              .registerStreamConsumer(
                RegisterStreamConsumerRequest(consumerName, streamArn),
                context,
                isCbor = false
              )
              .rethrow
            _ <- IO.sleep(
              cacheConfig.registerStreamConsumerDuration.plus(400.millis)
            )
            _ <- cache
              .deregisterStreamConsumer(
                DeregisterStreamConsumerRequest(
                  None,
                  Some(consumerName),
                  Some(streamArn)
                ),
                context,
                isCbor = false
              )
              .rethrow
            describeStreamConsumerReq = DescribeStreamConsumerRequest(
              None,
              Some(consumerName),
              Some(streamArn)
            )
            checkStream1 <- cache
              .describeStreamConsumer(
                describeStreamConsumerReq,
                context,
                isCbor = false
              )
              .rethrow
            _ <- IO.sleep(
              cacheConfig.deregisterStreamConsumerDuration.plus(400.millis)
            )
            checkStream2 <- cache.describeStreamConsumer(
              describeStreamConsumerReq,
              context,
              isCbor = false
            )
          } yield assert(
            checkStream1.consumerDescription.consumerStatus == ConsumerStatus.DELETING &&
              checkStream2.isLeft,
            s"$checkStream1\n$checkStream2"
          )
        }
  })
}
