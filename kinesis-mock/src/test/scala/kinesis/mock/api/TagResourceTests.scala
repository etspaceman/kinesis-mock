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

class TagResourceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should add tags to a stream by ARN")(PropF.forAllF {
    (streamArn: StreamArn, tags: Tags) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = TagResourceRequest(streamArn.streamArn, tags)
        res <- req.tagResource(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(streamArn).exists(_.tags == tags),
        s"req: $req\nres: $res"
      )
  })

  test("It should add tags to a consumer by ARN")(PropF.forAllF {
    (streamArn: StreamArn, consumerName: ConsumerName, tags: Tags) =>
      for
        now <- Utils.now
        stream0 = StreamData.create(1, streamArn, None, now)
        consumer = Consumer.create(streamArn, consumerName, now)
        stream = stream0.copy(consumers =
          stream0.consumers ++ Seq(consumerName -> consumer)
        )
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = TagResourceRequest(consumer.consumerArn.consumerArn, tags)
        res <- req.tagResource(streamsRef)
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .flatMap(_.consumers.get(consumerName))
          .exists(_.tags == tags),
        s"req: $req\nres: $res"
      )
  })
