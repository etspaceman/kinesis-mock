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

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

final case class RemoveTagsFromStreamRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    tagKeys: Vector[String]
):
  // https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RemoveTagsFromStream.html
  // https://docs.aws.amazon.com/streams/latest/dev/tagging.html
  // https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
  def removeTagsFromStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    streamsRef.modify(streams =>
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
                    CommonValidations.validateTagKeys(tagKeys), {
                      val numberOfTags = tagKeys.length
                      if numberOfTags > 10 then
                        InvalidArgumentException(
                          s"Can only remove 50 tags with a single request. Request contains $numberOfTags tags"
                        ).asLeft
                      else Right(())
                    }
                  ).mapN((_, _) => stream)
                )
            )
            .map(stream =>
              (
                streams
                  .updateStream(stream.copy(tags = stream.tags -- tagKeys)),
                ()
              )
            )
        }
        .sequenceWithDefault(streams)
    )

object RemoveTagsFromStreamRequest:
  given removeTagsFromStreamRequestCirceEncoder
      : circe.Encoder[RemoveTagsFromStreamRequest] =
    circe.Encoder.forProduct3("StreamName", "StreamARN", "TagKeys")(x =>
      (x.streamName, x.streamArn, x.tagKeys)
    )
  given removeTagsFromStreamRequestCirceDecoder
      : circe.Decoder[RemoveTagsFromStreamRequest] = x =>
    for
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      tagKeys <- x.downField("TagKeys").as[Vector[String]]
    yield RemoveTagsFromStreamRequest(streamName, streamArn, tagKeys)
  given removeTagsFromStreamRequestEncoder
      : Encoder[RemoveTagsFromStreamRequest] = Encoder.derive
  given removeTagsFromStreamRequestDecoder
      : Decoder[RemoveTagsFromStreamRequest] = Decoder.derive
  given removeTagsFromStreamRequestEq: Eq[RemoveTagsFromStreamRequest] =
    Eq.fromUniversalEquals
