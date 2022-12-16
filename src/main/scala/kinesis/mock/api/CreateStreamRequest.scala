package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class CreateStreamRequest(
    shardCount: Option[Int],
    streamModeDetails: Option[StreamModeDetails],
    streamName: StreamName
) {
  def createStream(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      onDemandStreamCountLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
      val shardCountOrDefault = shardCount.getOrElse(4)
      val streamArn = StreamArn(awsRegion, streamName, awsAccountId)
      (
        CommonValidations.validateStreamName(streamName),
        if (streams.streams.contains(streamArn))
          ResourceInUseException(
            s"Stream $streamName already exists"
          ).asLeft
        else Right(()),
        CommonValidations.validateShardCount(shardCountOrDefault),
        if (
          streams.streams.count { case (_, stream) =>
            stream.streamStatus == StreamStatus.CREATING
          } >= 5
        )
          LimitExceededException(
            "Limit for streams being created concurrently exceeded"
          ).asLeft
        else Right(()),
        CommonValidations.validateOnDemandStreamCount(
          streams,
          onDemandStreamCountLimit
        ),
        CommonValidations.validateShardLimit(
          shardCountOrDefault,
          streams,
          shardLimit
        )
      ).mapN { (_, _, _, _, _, _) =>
        val newStream =
          StreamData.create(shardCountOrDefault, streamArn, streamModeDetails)
        (
          streams
            .copy(streams = streams.streams ++ Seq(streamArn -> newStream)),
          ()
        )
      }.sequenceWithDefault(streams)
    }
}

object CreateStreamRequest {
  implicit val createStreamRequestCirceEncoder
      : circe.Encoder[CreateStreamRequest] =
    circe.Encoder.forProduct3("ShardCount", "StreamModeDetails", "StreamName")(
      x => (x.shardCount, x.streamModeDetails, x.streamName)
    )
  implicit val createStreamRequestCirceDecoder
      : circe.Decoder[CreateStreamRequest] = { x =>
    for {
      shardCount <- x.downField("ShardCount").as[Option[Int]]
      streamModeDetails <- x
        .downField("StreamModeDetails")
        .as[Option[StreamModeDetails]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield CreateStreamRequest(shardCount, streamModeDetails, streamName)
  }
  implicit val createStreamRequestEncoder: Encoder[CreateStreamRequest] =
    Encoder.derive
  implicit val createStreamRequestDecoder: Decoder[CreateStreamRequest] =
    Decoder.derive

  implicit val createStreamRequestEq: Eq[CreateStreamRequest] =
    Eq.fromUniversalEquals
}
