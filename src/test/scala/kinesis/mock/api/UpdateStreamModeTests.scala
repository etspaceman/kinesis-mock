package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class UpdateStreamModeTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should update the stream mode of a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val active: Streams =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        streamModeDetails = StreamModeDetails(StreamMode.ON_DEMAND)
        req = UpdateStreamModeRequest(streamArn, streamModeDetails)
        res <- req.updateStreamMode(streamsRef, 10)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.streamModeDetails == streamModeDetails
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should fail to update the stream mode when the limits are reached")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        val streams =
          Streams.empty.addStream(1, streamArn, None)

      val active: Streams =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

        for {
          streamsRef <- Ref.of[IO, Streams](active)
          streamModeDetails = StreamModeDetails(StreamMode.ON_DEMAND)
          req = UpdateStreamModeRequest(streamArn, streamModeDetails)
          res <- req.updateStreamMode(streamsRef, 0)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
}
