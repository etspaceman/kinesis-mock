package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
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
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val withTags =
        streams.findAndUpdateStream(streamArn)(s => s.copy(tags = tags))

      for {
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
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val exclusiveStartTagKey = tags.tags.keys.toVector(3)

      val withTags =
        streams.findAndUpdateStream(streamArn)(s => s.copy(tags = tags))

      for {
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
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val withTags =
        streams.findAndUpdateStream(streamArn)(s => s.copy(tags = tags))

      for {
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
