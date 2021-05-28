package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, IO}
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class CreateStreamRequest(shardCount: Int, streamName: StreamName) {
  def createStream(
      streamsRef: Ref[IO, Streams],
      shardSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]],
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  )(implicit C: Concurrent[IO]): IO[ValidatedResponse[Unit]] =
    streamsRef.get.flatMap { streams =>
      (
        CommonValidations.validateStreamName(streamName),
        if (streams.streams.contains(streamName))
          ResourceInUseException(
            s"Stream $streamName already exists"
          ).invalidNel
        else Valid(()),
        CommonValidations.validateShardCount(shardCount),
        if (
          streams.streams.count { case (_, stream) =>
            stream.streamStatus == StreamStatus.CREATING
          } >= 5
        )
          LimitExceededException(
            "Limit for streams being created concurrently exceeded"
          ).invalidNel
        else Valid(()),
        CommonValidations.validateShardLimit(shardCount, streams, shardLimit)
      ).traverseN { (_, _, _, _, _) =>
        val (newStream, shardSemaphoreKeys) =
          StreamData.create(shardCount, streamName, awsRegion, awsAccountId)
        for {
          _ <- streamsRef
            .update(x =>
              x.copy(streams = x.streams ++ List(streamName -> newStream))
            )
          shardSemaphores <- shardSemaphoreKeys
            .traverse(key => Semaphore[IO](1).map(s => key -> s))
          res <- shardSemaphoresRef.update(x => x ++ shardSemaphores)
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
