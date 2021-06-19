package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe a stream summary")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamSummaryRequest(streamName)
        res <- req.describeStreamSummary(streamsRef)
        streamDescriptionSummary = streams.streams
          .get(streamName)
          .map(s => StreamDescriptionSummary.fromStreamData(s))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescriptionSummary.contains(response.streamDescriptionSummary)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream does not exist")(PropF.forAllF {
    req: DescribeStreamSummaryRequest =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStreamSummary(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
