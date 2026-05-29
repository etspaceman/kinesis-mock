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

package kinesis.mock
package api

import cats.effect.{IO, Ref}
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DeleteResourcePolicyTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("Put then Delete should make Get return ResourceNotFoundException")(
    PropF.forAllF { (streamArn: StreamArn) =>
      val policy = """{"Version":"2012-10-17","Statement":[]}"""
      val arn = ResourceArn.Stream(streamArn)
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        _ <- PutResourcePolicyRequest(arn, policy).putResourcePolicy(streamsRef)
        delRes <- DeleteResourcePolicyRequest(arn)
          .deleteResourcePolicy(streamsRef)
        getRes <- GetResourcePolicyRequest(arn).getResourcePolicy(streamsRef)
      yield assert(
        delRes.isRight && (getRes match
          case Left(_: ResourceNotFoundException) => true
          case _                                  => false),
        s"delRes: $delRes\ngetRes: $getRes"
      )
    }
  )

  test("Delete on a never-set policy returns success (no-op)")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- DeleteResourcePolicyRequest(ResourceArn.Stream(streamArn))
          .deleteResourcePolicy(streamsRef)
      yield assert(res.isRight, s"res: $res")
  })

  test("Delete on a consumer ARN round-trips")(PropF.forAllF {
    (streamArn: StreamArn, consumerName: ConsumerName) =>
      val policy = "{}"
      for
        now <- Utils.now
        stream0 = StreamData.create(1, streamArn, None, now)
        consumer = Consumer.create(streamArn, consumerName, now)
        stream = stream0.copy(consumers =
          stream0.consumers ++ Seq(consumerName -> consumer)
        )
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        arn = ResourceArn.Consumer(consumer.consumerArn)
        _ <- PutResourcePolicyRequest(arn, policy).putResourcePolicy(streamsRef)
        delRes <- DeleteResourcePolicyRequest(arn)
          .deleteResourcePolicy(streamsRef)
        s <- streamsRef.get
      yield assert(
        delRes.isRight && !s.resourcePolicies.contains(arn.resourceArn),
        s"delRes: $delRes"
      )
  })

  test("Delete on unknown ARN returns ResourceNotFoundException")(
    PropF.forAllF { (streamArn: StreamArn) =>
      for
        streamsRef <- Ref.of[IO, Streams](Streams.empty)
        res <- DeleteResourcePolicyRequest(ResourceArn.Stream(streamArn))
          .deleteResourcePolicy(streamsRef)
      yield assert(
        res match
          case Left(_: ResourceNotFoundException) => true
          case _                                  => false,
        s"res: $res"
      )
    }
  )
