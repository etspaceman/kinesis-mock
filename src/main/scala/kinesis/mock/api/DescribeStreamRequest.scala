package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStream.html
final case class DescribeStreamRequest(
    exclusiveStartShardId: Option[String],
    limit: Option[Int],
    streamName: StreamName
) {
  def describeStream(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[DescribeStreamResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap(stream =>
              (
                exclusiveStartShardId match {
                  case Some(shardId) =>
                    CommonValidations.validateShardId(shardId)
                  case None => Right(())
                },
                limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case _       => Right(())
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
  implicit val describeStreamRequestCirceEncoder
      : circe.Encoder[DescribeStreamRequest] =
    circe.Encoder.forProduct3("ExclusiveStartShardId", "Limit", "StreamName")(
      x => (x.exclusiveStartShardId, x.limit, x.streamName)
    )
  implicit val describeStreamRequestCirceDecoder
      : circe.Decoder[DescribeStreamRequest] = { x =>
    for {
      exclusiveStartShardId <- x
        .downField("ExclusiveStartShardId")
        .as[Option[String]]
      limit <- x.downField("Limit").as[Option[Int]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName)
  }
  implicit val describeStreamRequestEncoder: Encoder[DescribeStreamRequest] =
    Encoder.derive
  implicit val describeStreamRequestDecoder: Decoder[DescribeStreamRequest] =
    Decoder.derive
  implicit val describeStreamRequestEq: Eq[DescribeStreamRequest] =
    Eq.fromUniversalEquals
}
