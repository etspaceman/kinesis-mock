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

class ListTagsForResourceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test(
    "It should return the tags that AddTagsToStream wrote (unified storage)"
  )(
    PropF.forAllF { (streamArn: StreamArn, tags: Tags) =>
      for
        now <- Utils.now
        stream0 = StreamData.create(1, streamArn, None, now)
        stream = stream0.copy(tags = tags)
        streams = Streams.empty.updateStream(stream)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListTagsForResourceRequest(streamArn.streamArn, None)
        res <- req.listTagsForResource(streamsRef)
      yield assert(
        res.exists(r =>
          r.tags == TagList.fromTags(tags) && r.nextToken.isEmpty
        ),
        s"req: $req\nres: $res"
      )
    }
  )

  test("It should return tags for a consumer ARN")(PropF.forAllF {
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
        req = ListTagsForResourceRequest(
          consumer.consumerArn.consumerArn,
          None
        )
        res <- req.listTagsForResource(streamsRef)
      yield assert(
        res.exists(_.tags == TagList.fromTags(tags)),
        s"req: $req\nres: $res"
      )
  })
