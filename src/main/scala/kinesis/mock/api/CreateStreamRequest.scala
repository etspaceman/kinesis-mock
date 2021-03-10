package kinesis.mock
package api

import io.circe._

import kinesis.mock.models._

final case class CreateStreamRequest(shardCount: Int, streamName: String) {
  def createStream(
      streams: Streams,
      shardLimit: Int,
      awsRegion: AwsRegion,
      awsAccountId: String
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      _ <-
        if (streams.streams.find(_.name == streamName).nonEmpty)
          Left(
            ResourceInUseException(
              s"Stream $streamName already exists"
            )
          )
        else Right(())
      _ <-
        if (shardCount < 1)
          Left(InvalidArgumentException("ShardCount must be > 1"))
        else Right(())
      _ <-
        if (
          streams.streams.filter(_.status == StreamStatus.CREATING).length >= 5
        )
          Left(
            LimitExceededException(
              "Limit for streams being created concurrently exceeded"
            )
          )
        else Right(())
      _ <- CommonValidations.validateShardLimit(shardCount, streams, shardLimit)
    } yield streams.addStream(shardCount, streamName, awsRegion, awsAccountId)
}

object CreateStreamRequest {
  implicit val createStreamRequestCirceEncoder: Encoder[CreateStreamRequest] =
    Encoder.forProduct2("ShardCount", "StreamName")(x =>
      (x.shardCount, x.streamName)
    )
  implicit val createStreamRequestCirceDecoder: Decoder[CreateStreamRequest] = {
    x =>
      for {
        shardCount <- x.downField("ShardCount").as[Int]
        streamName <- x.downField("StreamName").as[String]
      } yield CreateStreamRequest(shardCount, streamName)

  }
}
