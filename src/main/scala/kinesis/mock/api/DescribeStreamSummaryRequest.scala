package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamSummary.html
final case class DescribeStreamSummaryRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def describeStreamSummary(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DescribeStreamSummaryResponse]] = {
    streamsRef.get.map(streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
                .map(stream =>
                  DescribeStreamSummaryResponse(
                    StreamDescriptionSummary.fromStreamData(stream)
                  )
                )
            )
        }
    )
  }
}

object DescribeStreamSummaryRequest {
  implicit val describeStreamSummaryRequestCirceEncoder
      : circe.Encoder[DescribeStreamSummaryRequest] =
    circe.Encoder.forProduct2("StreamName", "StreamARN")(x =>
      (x.streamName, x.streamArn)
    )
  implicit val describeStreamSummaryRequestCirceDecoder
      : circe.Decoder[DescribeStreamSummaryRequest] = x =>
    for {
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield DescribeStreamSummaryRequest(streamName, streamArn)
  implicit val describeStreamSummaryRequestEncoder
      : Encoder[DescribeStreamSummaryRequest] = Encoder.derive
  implicit val describeStreamSummaryRequestDecoder
      : Decoder[DescribeStreamSummaryRequest] = Decoder.derive
  implicit val describeStreamSummaryRequestEq
      : Eq[DescribeStreamSummaryRequest] = Eq.fromUniversalEquals
}
