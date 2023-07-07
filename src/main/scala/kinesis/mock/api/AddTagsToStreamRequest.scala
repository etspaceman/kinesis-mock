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
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_AddTagsToStream.html
// https://docs.aws.amazon.com/streams/latest/dev/tagging.html
// https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
final case class AddTagsToStreamRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    tags: Tags
) {
  def addTagsToStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    CommonValidations
      .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
      .flatMap { case (name, _) =>
        CommonValidations
          .validateStreamName(name)
          .flatMap(_ =>
            CommonValidations
              .findStream(StreamArn(awsRegion, name, awsAccountId), streams)
              .flatMap(stream =>
                (
                  CommonValidations.validateTagKeys(tags.tags.keys), {
                    val valuesTooLong =
                      tags.tags.values.filter(x => x.length() > 256)
                    if (valuesTooLong.nonEmpty)
                      InvalidArgumentException(
                        s"Values must be less than 256 characters. Invalid values: ${valuesTooLong.mkString(", ")}"
                      ).asLeft
                    else Right(())
                  }, {
                    val invalidValues = tags.tags.values.filterNot(x =>
                      x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*)$")
                    )
                    if (invalidValues.nonEmpty)
                      InvalidArgumentException(
                        s"Values contain invalid characters. Invalid values: ${invalidValues.mkString(", ")}"
                      ).asLeft
                    else Right(())
                  }, {
                    val numberOfTags = tags.size
                    if (numberOfTags > 10)
                      InvalidArgumentException(
                        s"Can only add 10 tags with a single request. Request contains $numberOfTags tags"
                      ).asLeft
                    else Right(())
                  }, {
                    val totalTagsAfterAppend = (stream.tags |+| tags).size
                    if (totalTagsAfterAppend > 50)
                      InvalidArgumentException(
                        s"AWS resources can only have 50 tags. Request would result in $totalTagsAfterAppend tags"
                      ).asLeft
                    else Right(())
                  }
                ).mapN((_, _, _, _, _) => stream)
              )
          )
          .map(stream =>
            (streams.updateStream(stream.copy(tags = stream.tags |+| tags)), ())
          )
      }
      .sequenceWithDefault(streams)
  }
}

object AddTagsToStreamRequest {
  implicit val addTagsToStreamRequestCirceEncoder
      : circe.Encoder[AddTagsToStreamRequest] =
    circe.Encoder.forProduct3("StreamName", "StreamARN", "Tags")(x =>
      (x.streamName, x.streamArn, x.tags)
    )
  implicit val addTagsToStreamRequestCirceDecoder
      : circe.Decoder[AddTagsToStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      tags <- x.downField("Tags").as[Tags]
    } yield AddTagsToStreamRequest(streamName, streamArn, tags)
  }
  implicit val addTagsToStreamRequestEncoder: Encoder[AddTagsToStreamRequest] =
    Encoder.derive
  implicit val addTagsToStreamRequestDecoder: Decoder[AddTagsToStreamRequest] =
    Decoder.derive
  implicit val addTagsToStreamRequestEq: Eq[AddTagsToStreamRequest] =
    Eq.fromUniversalEquals
}
