package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStream.html
final case class DescribeStreamRequest(
    exclusiveStartShardId: Option[String],
    limit: Option[Int],
    streamName: StreamName
) {
  def describeStream(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[DescribeStreamResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen(stream =>
              (
                exclusiveStartShardId match {
                  case Some(shardId) =>
                    CommonValidations.validateShardId(shardId)
                  case None => Valid(())
                },
                limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case _       => Valid(())
                }
              ).mapN((_, _) =>
                DescribeStreamResponse(
                  StreamDescription
                    .fromStreamData(stream, exclusiveStartShardId, limit)
                )
              )
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
        streamName <- x.downField("StreamName").as[StreamName]
      } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName)
  }
  implicit val describeStreamRequestEq: Eq[DescribeStreamRequest] =
    Eq.fromUniversalEquals
}
