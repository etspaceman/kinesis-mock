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
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeregisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should deregister stream consumers by consumerName")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
      val streamArn =
        streams.streams.get(consumerArn.streamArn).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          streamArn
        )
        res <- req.deregisterStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(consumerArn.streamArn).exists { stream =>
          stream.consumers
            .get(consumerArn.consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should deregister stream consumers by consumerArn")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(Some(consumerArn), None, None)
        res <- req.deregisterStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(consumerArn.streamArn).exists { stream =>
          stream.consumers
            .get(consumerArn.consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer is not active")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(Some(consumerArn), None, None)
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer does not exist")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeregisterStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          Some(consumerArn.streamArn)
        )
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test(
    "It should reject if a consumerName is provided without a streamArn"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          None
        )
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test(
    "It should reject if a streamArn is provided without a consumerName"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(
          None,
          None,
          Some(consumerArn.streamArn)
        )
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })
}
