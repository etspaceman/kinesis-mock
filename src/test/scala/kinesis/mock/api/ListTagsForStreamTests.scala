package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListTagsForStreamTests extends munit.ScalaCheckSuite {
  property("It should list tags")(forAll {
    (
        streamName: StreamName,
        tags: Tags,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      val req =
        ListTagsForStreamRequest(None, None, streamName)
      val res = req.listTagsForStream(withTags)

      (res.isValid && res.exists { case response =>
        tags == response.tags
      }) :| s"req: $req\nres: $res"
  })

  property("It should fliter the listing by exclusiveStartTagKey")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val exclusiveStartTagKey = tags.tags.keys.toList(3)

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      val req =
        ListTagsForStreamRequest(Some(exclusiveStartTagKey), None, streamName)
      val res = req.listTagsForStream(withTags)

      (res.isValid && res.exists { case response =>
        tags.copy(tags = tags.tags.slice(4, 10)) == response.tags
      }) :| s"req: $req\nres: $res"
  })

  property("It should limit the listing")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val withTags =
        streams.findAndUpdateStream(streamName)(s => s.copy(tags = tags))

      val req =
        ListTagsForStreamRequest(None, Some(5), streamName)
      val res = req.listTagsForStream(withTags)

      (res.isValid && res.exists { case response =>
        tags.copy(tags =
          tags.tags.take(5)
        ) == response.tags && response.hasMoreTags
      }) :| s"req: $req\nres: $res"
  })
}
