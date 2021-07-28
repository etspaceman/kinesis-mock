package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Resource

class RemoveTagsFromStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should remove tags")(PropF.forAllF {
    (
        streamName: StreamName,
        tags: Tags
    ) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context, false)
            .rethrow
          _ <- cache
            .addTagsToStream(
              AddTagsToStreamRequest(streamName, tags),
              context,
              false
            )
            .rethrow
          _ <- cache.removeTagsFromStream(
            RemoveTagsFromStreamRequest(streamName, tags.tags.keys.toVector),
            context,
            false
          )
          res <- cache
            .listTagsForStream(
              ListTagsForStreamRequest(None, None, streamName),
              context,
              false
            )
            .rethrow
        } yield assert(res.tags.tags.isEmpty)
      )
  })
}
