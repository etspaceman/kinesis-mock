package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamSummary.html
final case class DescribeStreamSummaryRequest(
    streamName: StreamName
) {
  def describeStreamSummary(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[DescribeStreamSummaryResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .map(stream =>
              DescribeStreamSummaryResponse(
                StreamDescriptionSummary.fromStreamData(stream)
              )
            )
        )
    )
}

object DescribeStreamSummaryRequest {
  implicit val describeStreamSummaryRequestEncoder
      : Encoder[DescribeStreamSummaryRequest] =
    Encoder.forProduct1("StreamName")(_.streamName)
  implicit val describeStreamSummaryRequestDecoder
      : Decoder[DescribeStreamSummaryRequest] =
    _.downField("StreamName")
      .as[StreamName]
      .map(DescribeStreamSummaryRequest.apply)
  implicit val describeStreamSummaryRequestEq
      : Eq[DescribeStreamSummaryRequest] = Eq.fromUniversalEquals
}
