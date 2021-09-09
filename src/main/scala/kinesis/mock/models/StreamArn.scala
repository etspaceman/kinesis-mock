package kinesis.mock.models

import scala.util.Try

import cats.Eq
import cats.syntax.all._
import io.circe._

final case class StreamArn(
    awsRegion: AwsRegion,
    streamName: StreamName,
    awsAccountId: AwsAccountId
) {
  val streamArn =
    s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName"
  override def toString: String = streamArn
}

object StreamArn {
  def fromArn(streamArn: String): Either[String, StreamArn] = {
    for {
      streamName <- Try(streamArn.split("/")(1)).toEither.bimap(
        e => s"Could not get stream name from ARN: ${e.getMessage}",
        StreamName.apply
      )
      streamParts = streamArn.split(":")
      awsRegion <- Try(streamParts(3)).toEither
        .leftMap(_.getMessage)
        .flatMap(
          AwsRegion
            .withNameEither(_)
            .leftMap(e => s"Could not get awsRegion from ARN: ${e.getMessage}")
        )
      awsAccountId <- Try(streamParts(4)).toEither.bimap(
        e => s"Could not get awsAccountId from ARN: ${e.getMessage}",
        AwsAccountId.apply
      )
    } yield StreamArn(awsRegion, streamName, awsAccountId)
  }

  implicit val streamArnCirceEncoder: Encoder[StreamArn] =
    Encoder[String].contramap(_.streamArn)
  implicit val streamArnCirceDecoder: Decoder[StreamArn] =
    Decoder[String].emap(StreamArn.fromArn)
  implicit val streamArnCirceKeyEncoder: KeyEncoder[StreamArn] =
    KeyEncoder[String].contramap(_.streamArn)
  implicit val streamArnCirceKeyDecoder: KeyDecoder[StreamArn] =
    KeyDecoder.instance(StreamArn.fromArn(_).toOption)
  implicit val streamArnEq: Eq[StreamArn] = (x, y) =>
    x.awsRegion === y.awsRegion &&
      x.streamName === y.streamName &&
      x.awsAccountId === y.awsAccountId &&
      x.streamArn === y.streamArn
  implicit val streamArnOrdering: Ordering[StreamArn] =
    (x: StreamArn, y: StreamArn) =>
      Ordering[String].compare(x.streamArn, y.streamArn)
}
