package kinesis.mock.cache

import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class RemoveTagsFromStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should remove tags")(PropF.forAllF {
    (
        streamName: StreamName,
        tags: Tags,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(streamName, 1),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- cache
          .addTagsToStream(
            AddTagsToStreamRequest(streamName, tags),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- cache.removeTagsFromStream(
          RemoveTagsFromStreamRequest(streamName, tags.tags.keys.toVector),
          context,
          false,
          Some(awsRegion)
        )
        res <- cache
          .listTagsForStream(
            ListTagsForStreamRequest(None, None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(res.tags.tags.isEmpty)
  })
}
