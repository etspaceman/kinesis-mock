package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamSummary.html
final case class DescribeStreamSummaryRequest(
    streamName: StreamName
) {
  def describeStreamSummary(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[DescribeStreamSummaryResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
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
  implicit val describeStreamSummaryRequestCirceEncoder
      : circe.Encoder[DescribeStreamSummaryRequest] =
    circe.Encoder.forProduct1("StreamName")(_.streamName)
  implicit val describeStreamSummaryRequestCirceDecoder
      : circe.Decoder[DescribeStreamSummaryRequest] =
    _.downField("StreamName")
      .as[StreamName]
      .map(DescribeStreamSummaryRequest.apply)
  implicit val describeStreamSummaryRequestEncoder
      : Encoder[DescribeStreamSummaryRequest] = Encoder.derive
  implicit val describeStreamSummaryRequestDecoder
      : Decoder[DescribeStreamSummaryRequest] = Decoder.derive
  implicit val describeStreamSummaryRequestEq
      : Eq[DescribeStreamSummaryRequest] = Eq.fromUniversalEquals
}
