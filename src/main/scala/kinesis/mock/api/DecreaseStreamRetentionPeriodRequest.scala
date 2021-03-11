package kinesis.mock
package api

import scala.concurrent.duration._

import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DecreaseStreamRetention.html
final case class DecreaseStreamRetentionRequest(
    retentionPeriodHours: Int,
    streamName: String
) {
  def decreaseStreamRetention(
      streams: Streams
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      stream <- CommonValidations.findStream(streamName, streams)
      _ <- CommonValidations.validateRetentionPeriodHours(retentionPeriodHours)
      _ <-
        if (stream.retentionPeriod.toHours < retentionPeriodHours)
          Left(
            InvalidArgumentException(
              s"Provided RetentionPeriodHours $retentionPeriodHours is greater than the currently defined retention period ${stream.retentionPeriod.toHours}"
            )
          )
        else Right(())
    } yield streams.updateStream(
      stream.copy(retentionPeriod = retentionPeriodHours.hours)
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
}
