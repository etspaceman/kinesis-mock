package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class AddTagsToStreamTests extends KinesisMockSuite {
  test("It should add tags to a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        tags: Tags
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = AddTagsToStreamRequest(streamName, tags)
        res <- req.addTagsToStream(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.tags == tags
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should overwrite tags to a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val tagKey = tagKeyGen.one
      val tagValue = tagValueGen.one
      val tags = Tags(Map(tagKey -> tagValue))
      val initialTags = Tags(Map(tagKey -> "initial"))

      val streamsWithTag = streams.findAndUpdateStream(streamName)(stream =>
        stream.copy(tags = initialTags)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](streamsWithTag)
        req = AddTagsToStreamRequest(streamName, tags)
        res <- req.addTagsToStream(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.tags == tags
        },
        s"req: $req\nres: $res"
      )
  })
}
