package kinesis.mock.cache

import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class ListShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should list shards")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(5, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        res <- cache
          .listShards(
            ListShardsRequest(None, None, None, None, None, Some(streamName)),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(
        res.shards.length == 5,
        s"$res"
      )
  })
}
