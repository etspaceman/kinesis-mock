package kinesis.mock
package api

import scala.concurrent.duration._

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DecreaseStreamRetention.html
final case class DecreaseStreamRetentionRequest(
    retentionPeriodHours: Int,
    streamName: String
) {
  def decreaseStreamRetention(
      streams: Streams
  ): ValidatedNel[KinesisMockException, Streams] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.validateRetentionPeriodHours(retentionPeriodHours),
          if (stream.retentionPeriod.toHours < retentionPeriodHours)
            InvalidArgumentException(
              s"Provided RetentionPeriodHours $retentionPeriodHours is greater than the currently defined retention period ${stream.retentionPeriod.toHours}"
            ).invalidNel
          else Valid(())
        ).mapN((_, _, _) =>
          streams.updateStream(
            stream.copy(retentionPeriod = retentionPeriodHours.hours)
          )
        )
      )
}

object DecreaseStreamRetentionRequest {
  implicit val decreaseStreamRetentionRequestEncoder
      : Encoder[DecreaseStreamRetentionRequest] =
    Encoder.forProduct2("RetentionPeriodHours", "StreamName")(x =>
      (x.retentionPeriodHours, x.streamName)
    )
  implicit val decreaseStreamRetentionRequestDecoder
      : Decoder[DecreaseStreamRetentionRequest] = { x =>
    for {
      retentionPeriodHours <- x.downField("RetentionPeriod").as[Int]
      streamName <- x.downField("StreamName").as[String]
    } yield DecreaseStreamRetentionRequest(retentionPeriodHours, streamName)
  }
  implicit val decreaseStreamRetentionEq: Eq[DecreaseStreamRetentionRequest] =
    Eq.fromUniversalEquals
}
