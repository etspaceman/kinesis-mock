package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class ListStreamsRequest(
    exclusiveStartStreamName: Option[StreamName],
    limit: Option[Int]
) {
  def listStreams(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[ListStreamsResponse]] = streamsRef.get.map(streams =>
    (
      exclusiveStartStreamName match {
        case Some(streamName) =>
          CommonValidations.validateStreamName(streamName)
        case None => Valid(())
      },
      limit match {
        case Some(l) => CommonValidations.validateLimit(l)
        case None    => Valid(())
      }
    ).mapN((_, _) => {
      val allStreams = streams.streams.keys.toList
      val lastStreamIndex = allStreams.length - 1
      val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
      val firstIndex = exclusiveStartStreamName
        .map(x => allStreams.indexWhere(_ == x) + 1)
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
  implicit val listStreamsRequestCirceEncoder: Encoder[ListStreamsRequest] =
    Encoder.forProduct2("ExclusiveStartStreamName", "Limit")(x =>
      (x.exclusiveStartStreamName, x.limit)
    )

  implicit val listStreamsRequestCirceDecoder: Decoder[ListStreamsRequest] =
    x =>
      for {
        exclusiveStartStreamName <- x
          .downField("ExclusiveStartStreamName")
          .as[Option[StreamName]]
        limit <- x.downField("Limit").as[Option[Int]]
      } yield ListStreamsRequest(exclusiveStartStreamName, limit)

  implicit val listStreamsRequestEq: Eq[ListStreamsRequest] =
    Eq.fromUniversalEquals
}
