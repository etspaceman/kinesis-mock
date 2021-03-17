package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models.Streams

final case class ListTagsForStreamRequest(
    exclusiveStartTagKey: Option[String],
    limit: Option[Int],
    streamName: String
) {
  def listTagsForStream(
      streams: Streams
  ): ValidatedNel[KinesisMockException, ListTagsForStreamResponse] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          exclusiveStartTagKey match {
            case Some(tagKey) =>
              CommonValidations.validateTagKeys(List(tagKey))
            case None => Valid(())
          },
          limit match {
            case Some(l) => CommonValidations.validateLimit(l)
            case None    => Valid(())
          }
        ).mapN((_, _, _) => {
          val allTags = stream.tags.toList
          val lastTagIndex = allTags.length - 1
          val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
          val firstIndex = exclusiveStartTagKey
            .map(x => allTags.indexWhere(_._1 == x) + 1)
            .getOrElse(0)
          val lastIndex = Math.min(lim, lastTagIndex) + 1
          val tags = allTags.slice(firstIndex, lastIndex).toMap
          val hasMoreTags =
            if (lastTagIndex + 1 == lastIndex) false
            else true
          ListTagsForStreamResponse(hasMoreTags, tags)
        })
      )
}

object ListTagsForStreamRequest {
  implicit val listTagsForStreamRequestCirceEncoder
      : Encoder[ListTagsForStreamRequest] =
    Encoder.forProduct3("ExclusiveStartTagKey", "Limit", "StreamName")(x =>
      (x.exclusiveStartTagKey, x.limit, x.streamName)
    )

  implicit val listTagsForStreamRequestCirceDecoder
      : Decoder[ListTagsForStreamRequest] =
    x =>
      for {
        exclusiveStartTagKey <- x
          .downField("ExclusiveStartTagKey")
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
        streamName <- x.downField("StreamName").as[String]
      } yield ListTagsForStreamRequest(exclusiveStartTagKey, limit, streamName)

  implicit val listTagsForStreamRequestEq: Eq[ListTagsForStreamRequest] =
    Eq.fromUniversalEquals
}
