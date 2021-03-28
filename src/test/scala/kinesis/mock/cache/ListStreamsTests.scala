package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Gen

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests extends munit.CatsEffectSuite {
  test("It should list streams")(
    for {
      cacheConfig <- CacheConfig.read.load[IO]
      cache <- Cache(cacheConfig)
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
      )
      _ <- streamNames.traverse(streamName =>
        cache.createStream(CreateStreamRequest(1, streamName)).rethrow
      )
      res <- cache
        .listStreams(
          ListStreamsRequest(None, None)
        )
        .rethrow
    } yield assert(
      res.streamNames == streamNames.sorted,
      s"${res.streamNames}\n${streamNames.sorted}"
    )
  )
}
