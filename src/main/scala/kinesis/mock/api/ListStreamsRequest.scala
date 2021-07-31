package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

final case class ListStreamsRequest(
    exclusiveStartStreamName: Option[StreamName],
    limit: Option[Int]
) {
  def listStreams(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[ListStreamsResponse]] = streamsRef.get.map(streams =>
    (
      exclusiveStartStreamName match {
        case Some(streamName) =>
          CommonValidations.validateStreamName(streamName)
        case None => Right(())
      },
      limit match {
        case Some(l) => CommonValidations.validateLimit(l)
        case None    => Right(())
      }
    ).mapN((_, _) => {
      val allStreams = streams.streams.keys.toVector
      val lastStreamIndex = allStreams.length - 1
      val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
      val firstIndex = exclusiveStartStreamName
        .map(x => allStreams.indexOf(x) + 1)
        .getOrElse(0)
      val lastIndex = Math.min(firstIndex + lim, lastStreamIndex + 1)
      val streamNames = allStreams.slice(firstIndex, lastIndex)
      val hasMoreStreams =
        if (lastStreamIndex + 1 == lastIndex) false
        else true
      ListStreamsResponse(hasMoreStreams, streamNames)
    })
  )
}

object ListStreamsRequest {
  implicit val listStreamsRequestCirceEncoder
      : circe.Encoder[ListStreamsRequest] =
    circe.Encoder.forProduct2("ExclusiveStartStreamName", "Limit")(x =>
      (x.exclusiveStartStreamName, x.limit)
    )

  implicit val listStreamsRequestCirceDecoder
      : circe.Decoder[ListStreamsRequest] =
    x =>
      for {
        exclusiveStartStreamName <- x
          .downField("ExclusiveStartStreamName")
          .as[Option[StreamName]]
        limit <- x.downField("Limit").as[Option[Int]]
      } yield ListStreamsRequest(exclusiveStartStreamName, limit)

  implicit val listStreamsRequestEncoder: Encoder[ListStreamsRequest] =
    Encoder.derive
  implicit val listStreamsRequestDecoder: Decoder[ListStreamsRequest] =
    Decoder.derive
  implicit val listStreamsRequestEq: Eq[ListStreamsRequest] =
    Eq.fromUniversalEquals
}
