package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe a stream summary")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamSummaryRequest(None, Some(streamArn))
        res <- req.describeStreamSummary(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescriptionSummary = streams.streams
          .get(streamArn)
          .map(s => StreamDescriptionSummary.fromStreamData(s))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescriptionSummary.contains(response.streamDescriptionSummary)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream does not exist")(PropF.forAllF {
    (
        req: DescribeStreamSummaryRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStreamSummary(streamsRef, awsRegion, awsAccountId)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
