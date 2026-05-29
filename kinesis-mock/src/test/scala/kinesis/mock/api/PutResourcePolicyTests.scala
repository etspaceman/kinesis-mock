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

class PutResourcePolicyTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should store a policy for a stream ARN")(PropF.forAllF {
    (streamArn: StreamArn) =>
      val policy = """{"Version":"2012-10-17","Statement":[]}"""
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = PutResourcePolicyRequest(ResourceArn.Stream(streamArn), policy)
        res <- req.putResourcePolicy(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.resourcePolicies
          .get(streamArn.streamArn)
          .contains(policy),
        s"req: $req\nres: $res"
      )
  })

  test("It should store a policy for a consumer ARN")(PropF.forAllF {
    (streamArn: StreamArn, consumerName: ConsumerName) =>
      val policy = """{"Version":"2012-10-17","Statement":[]}"""
      for
        now <- Utils.now
        stream0 = StreamData.create(1, streamArn, None, now)
        consumer = Consumer.create(streamArn, consumerName, now)
        stream = stream0.copy(consumers =
          stream0.consumers ++ Seq(consumerName -> consumer)
        )
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = PutResourcePolicyRequest(
          ResourceArn.Consumer(consumer.consumerArn),
          policy
        )
        res <- req.putResourcePolicy(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.resourcePolicies
          .get(consumer.consumerArn.consumerArn)
          .contains(policy),
        s"req: $req\nres: $res"
      )
  })

  test("It should reject an unknown stream ARN with ResourceNotFoundException")(
    PropF.forAllF { (streamArn: StreamArn) =>
      val policy = "{}"
      for
        streamsRef <- Ref.of[IO, Streams](Streams.empty)
        req = PutResourcePolicyRequest(ResourceArn.Stream(streamArn), policy)
        res <- req.putResourcePolicy(streamsRef)
      yield assert(
        res match
          case Left(_: ResourceNotFoundException) => true
          case _                                  => false,
        s"req: $req\nres: $res"
      )
    }
  )
