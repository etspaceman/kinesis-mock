package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.AwsRegion
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests extends munit.CatsEffectSuite {
  test("It should list streams")(PropF.forAllF { awsRegion: AwsRegion =>
    for {
      cacheConfig <- CacheConfig.read
      cache <- Cache(cacheConfig)
      context = LoggingContext.create
      streamNames <- IO(
        Gen
          .listOfN(5, streamNameArbitrary.arbitrary)
          .suchThat(streamNames =>
            streamNames
              .groupBy(identity)
              .collect { case (_, x) if x.length > 1 => x }
              .isEmpty
          )
          .one
          .sorted
      )
      _ <- streamNames.traverse(streamName =>
        cache
          .createStream(
            CreateStreamRequest(streamName, 1),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      )
      res <- cache
        .listStreams(
          ListStreamsRequest(None, None),
          context,
          false,
          Some(awsRegion)
        )
        .rethrow
    } yield assert(
      res.streamNames == streamNames,
      s"${res.streamNames}\n${streamNames}"
    )
  })
}
