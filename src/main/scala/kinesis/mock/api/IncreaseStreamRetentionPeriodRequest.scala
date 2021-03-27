package kinesis.mock
package api

import scala.concurrent.duration._

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_IncreaseStreamRetention.html
final case class IncreaseStreamRetentionRequest(
    retentionPeriodHours: Int,
    streamName: StreamName
) {
  def increaseStreamRetention(
      streams: Streams
  ): ValidatedNel[KinesisMockException, Streams] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.validateRetentionPeriodHours(retentionPeriodHours),
          CommonValidations.isStreamActive(streamName, streams),
          if (stream.retentionPeriod.toHours > retentionPeriodHours)
            InvalidArgumentException(
              s"Provided RetentionPeriodHours $retentionPeriodHours is less than the currently defined retention period ${stream.retentionPeriod.toHours}"
            ).invalidNel
          else Valid(())
        ).mapN((_, _, _, _) =>
          streams.updateStream(
            stream.copy(retentionPeriod = retentionPeriodHours.hours)
          )
        )
      )
}

object IncreaseStreamRetentionRequest {
  implicit val increaseStreamRetentionRequestEncoder
      : Encoder[IncreaseStreamRetentionRequest] =
    Encoder.forProduct2("RetentionPeriodHours", "StreamName")(x =>
      (x.retentionPeriodHours, x.streamName)
    )
  implicit val increaseStreamRetentionRequestDecoder
      : Decoder[IncreaseStreamRetentionRequest] = { x =>
    for {
      retentionPeriodHours <- x.downField("RetentionPeriod").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield IncreaseStreamRetentionRequest(retentionPeriodHours, streamName)
  }
  implicit val increaseStreamRetentionRequestEq
      : Eq[IncreaseStreamRetentionRequest] = Eq.fromUniversalEquals
}
