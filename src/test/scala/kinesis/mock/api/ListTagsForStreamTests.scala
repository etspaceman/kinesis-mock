package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._
import cats.effect.Ref

class ListTagsForStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list tags")(PropF.forAllF {
    (
        streamName: StreamName,
        tags: Tags,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      for {
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(None, None, streamName)
        res <- req.listTagsForStream(streamsRef)
      } yield assert(
        res.isRight && res.exists { response =>
          tags == Tags.fromTagList(response.tags)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should fliter the listing by exclusiveStartTagKey")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val exclusiveStartTagKey = tags.tags.keys.toVector(3)

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      for {
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(
          Some(exclusiveStartTagKey),
          None,
          streamName
        )
        res <- req.listTagsForStream(streamsRef)
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
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      for {
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = ListTagsForStreamRequest(None, Some(5), streamName)
        res <- req.listTagsForStream(streamsRef)
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
