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

import kinesis.mock.instances.arbitrary.{given, _}
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListTagsForStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list tags")(PropF.forAllF {
    (
        streamArn: StreamArn,
        tags: Tags
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(100, streamArn, None, now)
        withTags = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(tags = tags)
        )
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(None, None, None, Some(streamArn))
        res <- req.listTagsForStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          tags == Tags.fromTagList(response.tags)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should fliter the listing by exclusiveStartTagKey")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams =
          Streams.empty.addStream(100, streamArn, None, now)
        tags = Gen
          .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
          .map(x => SortedMap.from(x))
          .map(Tags.apply)
          .one
        exclusiveStartTagKey = tags.tags.keys.toVector(3)
        withTags = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(tags = tags)
        )
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(
          Some(exclusiveStartTagKey),
          None,
          None,
          Some(streamArn)
        )
        res <- req.listTagsForStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          tags.copy(tags = tags.tags.slice(4, 10)) == Tags.fromTagList(
            response.tags
          )
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should limit the listing")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams =
          Streams.empty.addStream(100, streamArn, None, now)
        tags = Gen
          .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
          .map(x => SortedMap.from(x))
          .map(Tags.apply)
          .one
        withTags = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(tags = tags)
        )
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(None, Some(5), None, Some(streamArn))
        res <- req.listTagsForStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          tags.copy(tags = tags.tags.take(5)) == Tags.fromTagList(
            response.tags
          ) && response.hasMoreTags
        },
        s"req: $req\nres: $res"
      )
  })
}
