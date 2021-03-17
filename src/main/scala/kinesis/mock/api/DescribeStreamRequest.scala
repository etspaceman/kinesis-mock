package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
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
  ): ValidatedNel[KinesisMockException, DescribeStreamResponse] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          exclusiveStartShardId match {
            case Some(shardId) => CommonValidations.validateShardId(shardId)
            case None          => Valid(())
          },
          limit match {
            case Some(l) => CommonValidations.validateLimit(l)
            case _       => Valid(())
          }
        ).mapN((_, _, _) =>
          DescribeStreamResponse(
            StreamDescription
              .fromStreamData(stream, exclusiveStartShardId, limit)
          )
        )
      )
}

object DescribeStreamRequest {
  implicit val describeStreamRequestEncoder: Encoder[DescribeStreamRequest] =
    Encoder.forProduct3("ExclusiveStartShardId", "Limit", "StreamName")(x =>
      (x.exclusiveStartShardId, x.limit, x.streamName)
    )
  implicit val describeStreamRequestDecoder: Decoder[DescribeStreamRequest] = {
    x =>
      for {
        exclusiveStartShardId <- x
          .downField("ExclusiveStartShardId")
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
        streamName <- x.downField("StreamName").as[String]
      } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName)
  }
  implicit val describeStreamRequestEq: Eq[DescribeStreamRequest] =
    Eq.fromUniversalEquals
}
