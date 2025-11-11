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

package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should describe stream consumers by consumerName")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        updated = streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName, now)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
        consumer = updated.streams
          .get(consumerArn.streamArn)
          .flatMap(s => s.consumers.get(consumerArn.consumerName))
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          Some(consumerArn.streamArn)
        )
        res <- req.describeStreamConsumer(streamsRef)
      yield assert(
        res.isRight && res.exists { response =>
          consumer.contains(response.consumerDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should describe stream consumers by consumerArn")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams =
          Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        updated = streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName, now)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
        consumer = updated.streams
          .get(consumerArn.streamArn)
          .flatMap(s => s.consumers.get(consumerArn.consumerName))
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(Some(consumerArn), None, None)
        res <- req.describeStreamConsumer(streamsRef)
      yield assert(
        res.isRight && res.exists { response =>
          consumer.contains(response.consumerDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer does not exist")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        streamArn = streams.streams.get(consumerArn.streamArn).map(_.streamArn)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          streamArn
        )
        res <- req.describeStreamConsumer(streamsRef)
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject if a consumerName is provided without a streamArn"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        updated = streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName, now)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          None
        )
        res <- req.describeStreamConsumer(streamsRef)
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject if a streamArn is provided without a consumerName"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        updated = streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName, now)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          None,
          Some(consumerArn.streamArn)
        )
        res <- req.describeStreamConsumer(streamsRef)
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })
