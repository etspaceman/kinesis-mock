package kinesis.mock
package api

import cats.data._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamSummary.html
final case class DescribeStreamSummaryRequest(
    streamName: String
) {
  def describeStreamSummary(
      streams: Streams
  ): ValidatedNel[
    KinesisMockException,
    DescribeStreamSummaryResponse
  ] = CommonValidations
    .findStream(streamName, streams)
    .map(stream =>
      DescribeStreamSummaryResponse(
        StreamDescriptionSummary.fromStreamData(stream)
      )
    )
}

object DescribeStreamSummaryRequest {
  implicit val describeStreamSummaryRequestEncoder
      : Encoder[DescribeStreamSummaryRequest] =
    Encoder.forProduct1("StreamName")(_.streamName)
  implicit val describeStreamSummaryRequestDecoder
      : Decoder[DescribeStreamSummaryRequest] =
    _.downField("StreamName").as[String].map(DescribeStreamSummaryRequest.apply)
}
