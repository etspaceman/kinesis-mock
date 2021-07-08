package kinesis.mock
package cache

import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class RemoveTagsFromStreamTests extends KinesisMockSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should remove tags")(PropF.forAllF {
    (
        streamName: StreamName,
        tags: Tags
    ) =>
      for {
        cacheConfig <- CacheConfig.read
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
  })
}
