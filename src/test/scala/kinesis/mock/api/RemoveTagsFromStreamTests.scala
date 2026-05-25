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
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.{given, *}
import kinesis.mock.models.*
import kinesis.mock.syntax.scalacheck.*

class RemoveTagsFromStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should remove tags to a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        tags = Gen
          .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
          .map(x => SortedMap.from(x))
          .map(Tags.apply)
          .one
        withTags = streams.findAndUpdateStream(streamArn)(_.copy(tags = tags))
        removedTags = tags.tags.keys.take(3).toVector
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = RemoveTagsFromStreamRequest(None, Some(streamArn), removedTags)
        res <- req.removeTagsFromStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.tags == tags.copy(tags = tags.tags.filterNot { case (k, _) =>
            removedTags.contains(k)
          })
        },
        s"req: $req\nres: $res"
      )
  })
