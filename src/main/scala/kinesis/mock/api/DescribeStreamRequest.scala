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
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def describeStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DescribeStreamResponse]] = {
    streamsRef.get.map(streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
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
        }
    )
  }
}

object DescribeStreamRequest {
  implicit val describeStreamRequestCirceEncoder
      : circe.Encoder[DescribeStreamRequest] =
    circe.Encoder.forProduct4(
      "ExclusiveStartShardId",
      "Limit",
      "StreamName",
      "StreamARN"
    )(x => (x.exclusiveStartShardId, x.limit, x.streamName, x.streamArn))
  implicit val describeStreamRequestCirceDecoder
      : circe.Decoder[DescribeStreamRequest] = { x =>
    for {
      exclusiveStartShardId <- x
        .downField("ExclusiveStartShardId")
        .as[Option[String]]
      limit <- x.downField("Limit").as[Option[Int]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield DescribeStreamRequest(
      exclusiveStartShardId,
      limit,
      streamName,
      streamArn
    )
  }
  implicit val describeStreamRequestEncoder: Encoder[DescribeStreamRequest] =
    Encoder.derive
  implicit val describeStreamRequestDecoder: Decoder[DescribeStreamRequest] =
    Decoder.derive
  implicit val describeStreamRequestEq: Eq[DescribeStreamRequest] =
    Eq.fromUniversalEquals
}
