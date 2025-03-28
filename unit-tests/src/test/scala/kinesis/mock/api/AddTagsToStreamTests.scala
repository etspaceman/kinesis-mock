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

import kinesis.mock.instances.arbitrary.{given, _}
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class AddTagsToStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should add tags to a stream")(PropF.forAllF {
    (
        streamArn: StreamArn,
        tags: Tags
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = AddTagsToStreamRequest(None, Some(streamArn), tags)
        res <- req.addTagsToStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.tags == tags
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should overwrite tags to a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams =
          Streams.empty.addStream(1, streamArn, None, now)
        tagKey = tagKeyGen.one
        tagValue = tagValueGen.one
        tags = Tags(SortedMap(tagKey -> tagValue))
        initialTags = Tags(SortedMap(tagKey -> "initial"))
        streamsWithTag = streams.findAndUpdateStream(streamArn)(stream =>
          stream.copy(tags = initialTags)
        )
        streamsRef <- Ref.of[IO, Streams](streamsWithTag)
        req = AddTagsToStreamRequest(None, Some(streamArn), tags)
        res <- req.addTagsToStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.tags == tags
        },
        s"req: $req\nres: $res"
      )
  })
}
