package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class RemoveTagsFromStreamTests extends munit.ScalaCheckSuite {
  property("It should remove tags to a stream")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val withTags =
        streams.findAndUpdateStream(streamName)(_.copy(tags = tags))

      val removedTags = tags.tags.keys.take(3).toList

      val req = RemoveTagsFromStreamRequest(streamName, removedTags)

      val res = req.removeTagsFromStream(withTags)

      (res.isValid && res.exists { s =>
        s.streams.get(streamName).exists { stream =>
          stream.tags == tags.copy(tags = tags.tags.filterNot { case (k, _) =>
            removedTags.contains(k)
          })
        }
      }) :| s"req: $req\nres: $res"
  })
}
