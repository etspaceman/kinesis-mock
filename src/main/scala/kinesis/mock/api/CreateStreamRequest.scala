package kinesis.mock
package api

import cats.Eq
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, IO}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class CreateStreamRequest(shardCount: Int, streamName: StreamName) {
  def createStream(
      streamsRef: Ref[IO, Streams],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  )(implicit C: Concurrent[IO]): IO[Response[Unit]] =
    streamsRef.get.flatMap { streams =>
      (
        CommonValidations.validateStreamName(streamName),
        if (streams.streams.contains(streamName))
          ResourceInUseException(
            s"Stream $streamName already exists"
          ).asLeft
        else Right(()),
        CommonValidations.validateShardCount(shardCount),
        if (
          streams.streams.count { case (_, stream) =>
            stream.streamStatus == StreamStatus.CREATING
          } >= 5
        )
          LimitExceededException(
            "Limit for streams being created concurrently exceeded"
          ).asLeft
        else Right(()),
        CommonValidations.validateShardLimit(shardCount, streams, shardLimit)
      ).traverseN { (_, _, _, _, _) =>
        val newStream =
          StreamData.create(shardCount, streamName, awsRegion, awsAccountId)
        for {
          res <- streamsRef
            .update(x =>
              x.copy(streams = x.streams ++ List(streamName -> newStream))
            )
        } yield res
      }
    }
}

object CreateStreamRequest {
  implicit val createStreamRequestCirceEncoder
      : circe.Encoder[CreateStreamRequest] =
    circe.Encoder.forProduct2("ShardCount", "StreamName")(x =>
      (x.shardCount, x.streamName)
    )
  implicit val createStreamRequestCirceDecoder
      : circe.Decoder[CreateStreamRequest] = { x =>
    for {
      shardCount <- x.downField("ShardCount").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield CreateStreamRequest(shardCount, streamName)
  }
  implicit val createStreamRequestEncoder: Encoder[CreateStreamRequest] =
    Encoder.derive
  implicit val createStreamRequestDecoder: Decoder[CreateStreamRequest] =
    Decoder.derive

  implicit val createStreamRequestEq: Eq[CreateStreamRequest] =
    Eq.fromUniversalEquals
}
