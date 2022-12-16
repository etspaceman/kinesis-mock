package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateStreamMode.html
final case class UpdateStreamModeRequest(
    streamArn: StreamArn,
    streamModeDetails: StreamModeDetails
) {
  def updateStreamMode(
      streamsRef: Ref[IO, Streams],
      onDemandStreamCountLimit: Int
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
      CommonValidations
        .validateStreamArn(streamArn)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamArn, streams)
            .flatMap { stream =>
              (
                CommonValidations.isStreamActive(streamArn, streams),
                CommonValidations.validateOnDemandStreamCount(
                  streams,
                  onDemandStreamCountLimit
                )
              ).mapN((_, _) => stream)
            }
        )
        .map { stream =>
          (
            streams.updateStream(
              stream.copy(
                streamModeDetails = streamModeDetails,
                streamStatus = StreamStatus.UPDATING
              )
            ),
            ()
          )
        }
        .sequenceWithDefault(streams)
    }
}

object UpdateStreamModeRequest {
  implicit val updateStreamModeRequestCirceEncoder
      : circe.Encoder[UpdateStreamModeRequest] =
    circe.Encoder.forProduct2("StreamARN", "StreamModeDetails")(x =>
      (x.streamArn, x.streamModeDetails)
    )

  implicit val updateStreamModeRequestCirceDecoder
      : circe.Decoder[UpdateStreamModeRequest] = x =>
    for {
      streamArn <- x.downField("StreamARN").as[StreamArn]
      streamModeDetails <- x
        .downField("StreamModeDetails")
        .as[StreamModeDetails]
    } yield UpdateStreamModeRequest(streamArn, streamModeDetails)

  implicit val updateStreamModeRequestEncoder
      : Encoder[UpdateStreamModeRequest] = Encoder.derive
  implicit val updateStreamModeRequestDecoder
      : Decoder[UpdateStreamModeRequest] = Decoder.derive

  implicit val updateStreamModeRequestEq: Eq[UpdateStreamModeRequest] =
    Eq.fromUniversalEquals
}
