/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package api

import scala.collection.SortedMap

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class ListTagsForStreamRequest(
    exclusiveStartTagKey: Option[String],
    limit: Option[Int],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def listTagsForStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[ListTagsForStreamResponse]] =
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
                    exclusiveStartTagKey match {
                      case Some(tagKey) =>
                        CommonValidations.validateTagKeys(Vector(tagKey))
                      case None => Right(())
                    },
                    limit match {
                      case Some(l) => CommonValidations.validateLimit(l)
                      case None    => Right(())
                    }
                  ).mapN { (_, _) =>
                    val allTags = stream.tags.toVector
                    val lastTagIndex = allTags.length - 1
                    val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
                    val firstIndex = exclusiveStartTagKey
                      .map(x => allTags.indexWhere(_._1 == x) + 1)
                      .getOrElse(0)
                    val lastIndex = Math.min(firstIndex + lim, lastTagIndex + 1)
                    val tags =
                      SortedMap.from(allTags.slice(firstIndex, lastIndex))
                    val hasMoreTags =
                      if (lastTagIndex + 1 == lastIndex) false
                      else true
                    ListTagsForStreamResponse(
                      hasMoreTags,
                      TagList.fromTags(Tags(tags))
                    )
                  }
                )
            )
        }
    )
}

object ListTagsForStreamRequest {
  implicit val listTagsForStreamRequestCirceEncoder
      : circe.Encoder[ListTagsForStreamRequest] =
    circe.Encoder.forProduct4(
      "ExclusiveStartTagKey",
      "Limit",
      "StreamName",
      "StreamARN"
    )(x => (x.exclusiveStartTagKey, x.limit, x.streamName, x.streamArn))

  implicit val listTagsForStreamRequestCirceDecoder
      : circe.Decoder[ListTagsForStreamRequest] =
    x =>
      for {
        exclusiveStartTagKey <- x
          .downField("ExclusiveStartTagKey")
          .as[Option[String]]
        limit <- x.downField("Limit").as[Option[Int]]
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      } yield ListTagsForStreamRequest(
        exclusiveStartTagKey,
        limit,
        streamName,
        streamArn
      )

  implicit val listTagsForStreamRequestEncoder
      : Encoder[ListTagsForStreamRequest] = Encoder.derive
  implicit val listTagsForStreamRequestDecoder
      : Decoder[ListTagsForStreamRequest] = Decoder.derive
  implicit val listTagsForStreamRequestEq: Eq[ListTagsForStreamRequest] =
    Eq.fromUniversalEquals
}
