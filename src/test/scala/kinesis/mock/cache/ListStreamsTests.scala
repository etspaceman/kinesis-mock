package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Gen

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests extends munit.CatsEffectSuite {
  test("It should list streams")(
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
          .createStream(CreateStreamRequest(1, streamName), context, false)
          .rethrow
      )
      res <- cache
        .listStreams(
          ListStreamsRequest(None, None),
          context,
          false
        )
        .rethrow
    } yield assert(
      res.streamNames == streamNames,
      s"${res.streamNames}\n${streamNames}"
    )
  )
}
