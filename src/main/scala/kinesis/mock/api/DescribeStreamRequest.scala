package kinesis.mock
package api

import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStream.html
final case class DescribeStreamRequest(
    exclusiveStartShardId: Option[String],
    limit: Option[Int],
    streamName: String
) {
  def describeStream(
      streams: Streams
  ): Either[KinesisMockException, DescribeStreamResponse] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      stream <- CommonValidations.findStream(streamName, streams)
      _ <- exclusiveStartShardId match {
        case Some(shardId) => CommonValidations.validateShardId(shardId)
        case None          => Right(())
      }
      _ <- limit match {
        case Some(l) if (l < 1 || l > 10000) =>
          Left(InvalidArgumentException(s"Limit must be between 1 and 10000"))
        case _ => Right(())
      }
    } yield DescribeStreamResponse(
      StreamDescription.fromStreamData(stream, exclusiveStartShardId, limit)
    )
}

object DescribeStreamRequest {
  implicit val DescribeStreamRequestEncoder: Encoder[DescribeStreamRequest] =
    Encoder.forProduct3("ExclusiveStartShardId", "Limit", "StreamName")(x =>
      (x.exclusiveStartShardId, x.limit, x.streamName)
    )
  implicit val DescribeStreamRequestDecoder: Decoder[DescribeStreamRequest] = {
    x =>
      for {
        exclusiveStartShardId <- x
          .downField("ExclusiveStartShardId")
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
        streamName <- x.downField("StreamName").as[String]
      } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName)
  }
}
