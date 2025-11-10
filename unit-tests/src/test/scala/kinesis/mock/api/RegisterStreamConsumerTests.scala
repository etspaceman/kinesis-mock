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
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*
import kinesis.mock.syntax.scalacheck.*

class RegisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should register stream consumers")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = RegisterStreamConsumerRequest(
          consumerArn.consumerName,
          consumerArn.streamArn
        )
        res <- req.registerStreamConsumer(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(consumerArn.streamArn).exists { stream =>
          stream.consumers.contains(consumerArn.consumerName)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when there are 20 consumers")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        consumers = SortedMap.from(
          Gen
            .listOfN(20, Arbitrary.arbitrary[Consumer])
            .suchThat(x =>
              x.groupBy(_.consumerName)
                .collect { case (_, y) if y.length > 1 => x }
                .isEmpty
            )
            .one
            .map(c => c.consumerName -> c)
        )
        updated = streams.findAndUpdateStream(consumerArn.streamArn)(s =>
          s.copy(consumers = consumers)
        )
        streamsRef <- Ref.of[IO, Streams](updated)
        req = RegisterStreamConsumerRequest(
          consumerArn.consumerName,
          consumerArn.streamArn
        )
        res <- req.registerStreamConsumer(streamsRef)
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject when there are 5 consumers being created")(
    PropF.forAllF {
      (
        consumerArn: ConsumerArn
      ) =>
        for
          now <- Utils.now
          streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
          consumers = SortedMap.from(
            Gen
              .listOfN(5, Arbitrary.arbitrary[Consumer])
              .suchThat(x =>
                x.groupBy(_.consumerName)
                  .collect { case (_, y) if y.length > 1 => x }
                  .isEmpty
              )
              .map(_.map(c => c.copy(consumerStatus = ConsumerStatus.CREATING)))
              .one
              .map(c => c.consumerName -> c)
          )
          updated = streams.findAndUpdateStream(consumerArn.streamArn)(s =>
            s.copy(consumers = consumers)
          )
          streamsRef <- Ref.of[IO, Streams](updated)
          req = RegisterStreamConsumerRequest(
            consumerArn.consumerName,
            consumerArn.streamArn
          )
          res <- req.registerStreamConsumer(streamsRef)
        yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
