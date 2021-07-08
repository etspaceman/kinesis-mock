package kinesis.mock
package cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Gen

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.scalacheck._
import kinesis.mock.{KinesisMockSuite, LoggingContext}

class ListStreamsTests extends KinesisMockSuite {
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
      res.streamNames == streamNames.sorted,
      s"${res.streamNames}\n${streamNames.sorted}"
    )
  )
}
