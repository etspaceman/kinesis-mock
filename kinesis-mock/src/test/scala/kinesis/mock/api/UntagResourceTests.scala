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

class UntagResourceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should remove tags from a stream by ARN")(PropF.forAllF {
    (streamArn: StreamArn, tags: Tags) =>
      for
        now <- Utils.now
        stream0 = StreamData.create(1, streamArn, None, now)
        stream = stream0.copy(tags = tags)
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        keysToRemove = tags.tags.keys.take(1).toList
        req = UntagResourceRequest(streamArn.streamArn, keysToRemove)
        res <- req.untagResource(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists(st => keysToRemove.forall(k => !st.tags.tags.contains(k))),
        s"req: $req\nres: $res"
      )
  })

  test("It should silently ignore unknown tag keys for a stream")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UntagResourceRequest(streamArn.streamArn, List("does-not-exist"))
        res <- req.untagResource(streamsRef)
      yield assert(res.isRight, s"req: $req\nres: $res")
  })

  test("It should remove tags from a consumer by ARN")(PropF.forAllF {
    (streamArn: StreamArn, consumerName: ConsumerName, tags: Tags) =>
      for
        now <- Utils.now
        consumer0 = Consumer.create(streamArn, consumerName, now)
        consumer = consumer0.copy(tags = tags)
        stream0 = StreamData.create(1, streamArn, None, now)
        stream = stream0.copy(consumers =
          stream0.consumers ++ Seq(consumerName -> consumer)
        )
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        keysToRemove = tags.tags.keys.take(1).toList
        req = UntagResourceRequest(
          consumer.consumerArn.consumerArn,
          keysToRemove
        )
        res <- req.untagResource(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .flatMap(_.consumers.get(consumerName))
          .exists(c => keysToRemove.forall(k => !c.tags.tags.contains(k))),
        s"req: $req\nres: $res"
      )
  })
