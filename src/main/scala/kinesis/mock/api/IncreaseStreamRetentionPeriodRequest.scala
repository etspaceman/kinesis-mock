package kinesis.mock
package api

import scala.concurrent.duration._

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_IncreaseStreamRetention.html
final case class IncreaseStreamRetentionPeriodRequest(
    retentionPeriodHours: Int,
    streamName: StreamName
) {
  def increaseStreamRetention(
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
              if (stream.retentionPeriod.toHours > retentionPeriodHours)
                InvalidArgumentException(
                  s"Provided RetentionPeriodHours $retentionPeriodHours is less than the currently defined retention period ${stream.retentionPeriod.toHours}"
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

object IncreaseStreamRetentionPeriodRequest {
  implicit val increaseStreamRetentionRequestCirceEncoder
      : circe.Encoder[IncreaseStreamRetentionPeriodRequest] =
    circe.Encoder.forProduct2("RetentionPeriodHours", "StreamName")(x =>
      (x.retentionPeriodHours, x.streamName)
    )
  implicit val increaseStreamRetentionRequestCirceDecoder
      : circe.Decoder[IncreaseStreamRetentionPeriodRequest] = { x =>
    for {
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield IncreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName
    )
  }
  implicit val increaseStreamRetentionRequestEncoder
      : Encoder[IncreaseStreamRetentionPeriodRequest] = Encoder.derive
  implicit val increaseStreamRetentionRequestDecoder
      : Decoder[IncreaseStreamRetentionPeriodRequest] = Decoder.derive
  implicit val increaseStreamRetentionRequestEq
      : Eq[IncreaseStreamRetentionPeriodRequest] = Eq.fromUniversalEquals
}
