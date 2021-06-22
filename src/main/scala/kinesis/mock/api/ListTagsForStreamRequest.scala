package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
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
  ): IO[Response[ListTagsForStreamResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap(stream =>
              (
                exclusiveStartTagKey match {
                  case Some(tagKey) =>
                    CommonValidations.validateTagKeys(Vector(tagKey))
                  case None => Right(())
                },
                limit match {
                  case Some(l) => CommonValidations.validateLimit(l)
                  case None    => Right(())
                }
              ).mapN((_, _) => {
                val allTags = stream.tags.toVector
                val lastTagIndex = allTags.length - 1
                val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
                val firstIndex = exclusiveStartTagKey
                  .map(x => allTags.indexWhere(_._1 == x) + 1)
                  .getOrElse(0)
                val lastIndex = Math.min(firstIndex + lim, lastTagIndex + 1)
                val tags = Map.from(allTags.slice(firstIndex, lastIndex))
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
