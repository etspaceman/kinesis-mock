package kinesis.mock
package api

import scala.collection.SortedMap

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class ListTagsForStreamRequest(
    exclusiveStartTagKey: Option[String],
    limit: Option[Int],
    streamName: StreamName
) {
  def listTagsForStream(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[ListTagsForStreamResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen(stream =>
              (
                exclusiveStartTagKey match {
                  case Some(tagKey) =>
                    CommonValidations.validateTagKeys(List(tagKey))
                  case None => Valid(())
                },
                limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case None    => Valid(())
                }
              ).mapN((_, _) => {
                val allTags = stream.tags.toList
                val lastTagIndex = allTags.length - 1
                val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
                val firstIndex = exclusiveStartTagKey
                  .map(x => allTags.indexWhere(_._1 == x) + 1)
                  .getOrElse(0)
                val lastIndex = Math.min(firstIndex + lim, lastTagIndex + 1)
                val tags = SortedMap.from(allTags.slice(firstIndex, lastIndex))
                val hasMoreTags =
                  if (lastTagIndex + 1 == lastIndex) false
                  else true
                ListTagsForStreamResponse(
                  hasMoreTags,
                  TagList.fromTags(Tags(tags))
                )
              })
            )
        )
    )
}

object ListTagsForStreamRequest {
  implicit val listTagsForStreamRequestCirceEncoder
      : circe.Encoder[ListTagsForStreamRequest] =
    circe.Encoder.forProduct3("ExclusiveStartTagKey", "Limit", "StreamName")(
      x => (x.exclusiveStartTagKey, x.limit, x.streamName)
    )

  implicit val listTagsForStreamRequestCirceDecoder
      : circe.Decoder[ListTagsForStreamRequest] =
    x =>
      for {
        exclusiveStartTagKey <- x
          .downField("ExclusiveStartTagKey")
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
        streamName <- x.downField("StreamName").as[StreamName]
      } yield ListTagsForStreamRequest(exclusiveStartTagKey, limit, streamName)

  implicit val listTagsForStreamRequestEncoder
      : Encoder[ListTagsForStreamRequest] = Encoder.derive
  implicit val listTagsForStreamRequestDecoder
      : Decoder[ListTagsForStreamRequest] = Decoder.derive
  implicit val listTagsForStreamRequestEq: Eq[ListTagsForStreamRequest] =
    Eq.fromUniversalEquals
}
