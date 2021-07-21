package kinesis.mock
package api

import scala.concurrent.duration._

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DecreaseStreamRetention.html
final case class DecreaseStreamRetentionPeriodRequest(
    retentionPeriodHours: Int,
    streamName: StreamName
) {
  def decreaseStreamRetention(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    CommonValidations
      .validateStreamName(streamName)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .flatMap(stream =>
            (
              CommonValidations
                .validateRetentionPeriodHours(retentionPeriodHours),
              CommonValidations.isStreamActive(streamName, streams),
              if (stream.retentionPeriod.toHours < retentionPeriodHours)
                InvalidArgumentException(
                  s"Provided RetentionPeriodHours $retentionPeriodHours is greater than the currently defined retention period ${stream.retentionPeriod.toHours}"
                ).asLeft
              else Right(())
            ).mapN((_, _, _) => stream)
          )
      )
      .map(stream =>
        (
          streams.updateStream(
            stream.copy(retentionPeriod = retentionPeriodHours.hours)
          ),
          ()
        )
      )
      .sequenceWithDefault(streams)
  }
}

object DecreaseStreamRetentionPeriodRequest {
  implicit val decreaseStreamRetentionPeriodRequestCirceEncoder
      : circe.Encoder[DecreaseStreamRetentionPeriodRequest] =
    circe.Encoder.forProduct2("RetentionPeriodHours", "StreamName")(x =>
      (x.retentionPeriodHours, x.streamName)
    )
  implicit val decreaseStreamRetentionPeriodRequestCirceDecoder
      : circe.Decoder[DecreaseStreamRetentionPeriodRequest] = { x =>
    for {
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield DecreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName
    )
  }
  implicit val decreaseStreamRetentionPeriodRequestEncoder
      : Encoder[DecreaseStreamRetentionPeriodRequest] =
    Encoder.derive
  implicit val decreaseStreamRetentionPeriodRequestDecoder
      : Decoder[DecreaseStreamRetentionPeriodRequest] =
    Decoder.derive
  implicit val decreaseStreamRetentionEq
      : Eq[DecreaseStreamRetentionPeriodRequest] =
    Eq.fromUniversalEquals
}
