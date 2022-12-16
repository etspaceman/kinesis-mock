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

class RemoveTagsFromStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should remove tags to a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val tags: Tags = Gen
        .mapOfN(10, Gen.zip(tagKeyGen, tagValueGen))
        .map(x => SortedMap.from(x))
        .map(Tags.apply)
        .one

      val withTags =
        streams.findAndUpdateStream(streamArn)(_.copy(tags = tags))

      val removedTags = tags.tags.keys.take(3).toVector

      for {
        streamsRef <- Ref.of[IO, Streams](withTags)
        req = RemoveTagsFromStreamRequest(streamArn.streamName, removedTags)
        res <- req.removeTagsFromStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.tags == tags.copy(tags = tags.tags.filterNot { case (k, _) =>
            removedTags.contains(k)
          })
        },
        s"req: $req\nres: $res"
      )
  })
}
