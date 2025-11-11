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
import cats.syntax.all.*
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*
import kinesis.mock.syntax.scalacheck.*

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should list consumers")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(100, streamArn, None, now)
        consumers = SortedMap.from(
          Gen
            .listOfN(5, Arbitrary.arbitrary[Consumer])
            .suchThat(x =>
              x.groupBy(_.consumerName)
                .collect { case (_, y) if y.length > 1 => x }
                .isEmpty
            )
            .one
            .map(c => c.consumerName -> c)
        )
        withConsumers = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(consumers = consumers)
        )
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = ListStreamConsumersRequest(None, None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)
      yield assert(
        res.isRight && res.exists { response =>
          consumers.values.toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should list consumers when consumers are empty")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(100, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamConsumersRequest(None, None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)
      yield assert(
        res.isRight && res.exists(_.consumers.isEmpty),
        s"req: $req\nres: $res"
      )
  })

  test("It should paginate properly")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(100, streamArn, None, now)
        consumers = SortedMap.from(
          Gen
            .listOfN(10, Arbitrary.arbitrary[Consumer])
            .suchThat(x =>
              x.groupBy(_.consumerName)
                .collect { case (_, y) if y.length > 1 => x }
                .isEmpty
            )
            .one
            .map(c => c.consumerName -> c)
        )
        withConsumers = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(consumers = consumers)
        )
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = ListStreamConsumersRequest(Some(5), None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)
        paginatedRes <- res
          .traverse(result =>
            ListStreamConsumersRequest(
              Some(5),
              result.nextToken,
              streamArn,
              None
            ).listStreamConsumers(streamsRef)
          )
          .map(_.flatMap(identity))
      yield assert(
        res.isRight && paginatedRes.isRight && res.exists { response =>
          consumers.values
            .take(5)
            .toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        } && paginatedRes.exists { response =>
          consumers.values
            .takeRight(5)
            .toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.consumers.length)}\n" +
          s"paginatedResCount: ${paginatedRes.map(_.consumers.length)}}"
      )
  })
