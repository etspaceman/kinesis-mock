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

class GetResourcePolicyTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should return the policy after PutResourcePolicy on a stream ARN")(
    PropF.forAllF { (streamArn: StreamArn) =>
      val policy = """{"Version":"2012-10-17","Statement":[]}"""
      val arn = ResourceArn.Stream(streamArn)
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        _ <- PutResourcePolicyRequest(arn, policy).putResourcePolicy(streamsRef)
        res <- GetResourcePolicyRequest(arn).getResourcePolicy(streamsRef)
      yield assert(
        res.exists(_.policy == policy),
        s"res: $res"
      )
    }
  )

  test("It should return the policy after PutResourcePolicy on a consumer ARN")(
    PropF.forAllF { (streamArn: StreamArn, consumerName: ConsumerName) =>
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
        arn = ResourceArn.Consumer(consumer.consumerArn)
        _ <- PutResourcePolicyRequest(arn, policy).putResourcePolicy(streamsRef)
        res <- GetResourcePolicyRequest(arn).getResourcePolicy(streamsRef)
      yield assert(
        res.exists(_.policy == policy),
        s"res: $res"
      )
    }
  )

  test(
    "It should return ResourceNotFoundException when no policy was ever set"
  )(PropF.forAllF { (streamArn: StreamArn) =>
    for
      now <- Utils.now
      streams = Streams.empty.addStream(1, streamArn, None, now)
      streamsRef <- Ref.of[IO, Streams](streams)
      req = GetResourcePolicyRequest(ResourceArn.Stream(streamArn))
      res <- req.getResourcePolicy(streamsRef)
    yield assert(
      res.left.exists(_.isInstanceOf[ResourceNotFoundException]),
      s"res: $res"
    )
  })

  test("It should return ResourceNotFoundException for an unknown ARN")(
    PropF.forAllF { (streamArn: StreamArn) =>
      for
        streamsRef <- Ref.of[IO, Streams](Streams.empty)
        req = GetResourcePolicyRequest(ResourceArn.Stream(streamArn))
        res <- req.getResourcePolicy(streamsRef)
      yield assert(
        res.left.exists(_.isInstanceOf[ResourceNotFoundException]),
        s"res: $res"
      )
    }
  )
