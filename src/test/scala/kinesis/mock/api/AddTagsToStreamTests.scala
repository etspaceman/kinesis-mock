package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class AddTagsToStreamTests extends munit.ScalaCheckSuite {
  property("It should add tags to a stream")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        tags: Tags
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val req = AddTagsToStreamRequest(streamName, tags)

      val res = req.addTagsToStream(streams)

      (res.isValid && res.exists { s =>
        s.streams.get(streamName).exists { stream =>
          stream.tags == tags
        }
      }) :| s"req: $req\nres: $res"
  })

  property("It should overwrite tags to a stream")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val tagKey = tagKeyGen.one
      val tagValue = tagValueGen.one
      val tags = Tags(SortedMap(tagKey -> tagValue))
      val initialTags = Tags(SortedMap(tagKey -> "initial"))

      val streamsWithTag = streams.findAndUpdateStream(streamName)(stream =>
        stream.copy(tags = initialTags)
      )
      val req = AddTagsToStreamRequest(streamName, tags)

      val res = req.addTagsToStream(streamsWithTag)

      (res.isValid && res.exists { s =>
        s.streams.get(streamName).exists { stream =>
          stream.tags == tags
        }
      }) :| s"req: $req\nres: $res"
  })
}
