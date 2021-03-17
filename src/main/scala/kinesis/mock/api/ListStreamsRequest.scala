package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

import kinesis.mock.models.Streams
import cats.kernel.Eq

final case class ListStreamsRequest(
    exclusiveStartStreamName: Option[String],
    limit: Option[Int]
) {
  def listStreams(
      streams: Streams
  ): ValidatedNel[KinesisMockException, ListStreamsResponse] =
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
      val lastIndex = Math.min(lim, lastStreamIndex) + 1
      val streamNames = allStreams.slice(firstIndex, lastIndex)
      val hasMoreStreams =
        if (lastStreamIndex + 1 == lastIndex) false
        else true
      ListStreamsResponse(hasMoreStreams, streamNames)
    })
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
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
      } yield ListStreamsRequest(exclusiveStartStreamName, limit)

  implicit val listStreamsRequestEq: Eq[ListStreamsRequest] =
    Eq.fromUniversalEquals
}
