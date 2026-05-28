/*
 * Copyright 2021-2026 io.github.etspaceman
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
import io.circe

import kinesis.mock.models.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_ListTagsForResource.html
final case class ListTagsForResourceRequest(
    resourceArn: String,
    nextToken: Option[String]
):
  def listTagsForResource(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[ListTagsForResourceResponse]] =
    streamsRef.get.map { streams =>
      ResourceArn
        .fromString(resourceArn)
        .flatMap {
          case ResourceArn.Stream(streamArn) =>
            CommonValidations
              .findStream(streamArn, streams)
              .map(_.tags)
          case ResourceArn.Consumer(consumerArn) =>
            CommonValidations
              .findStreamByConsumerArn(consumerArn, streams)
              .map { case (consumer, _) => consumer.tags }
        }
        .map(tags =>
          ListTagsForResourceResponse(TagList.fromTags(tags), None)
        )
    }

object ListTagsForResourceRequest:
  given listTagsForResourceRequestCirceEncoder
      : circe.Encoder[ListTagsForResourceRequest] =
    circe.Encoder.forProduct2("ResourceARN", "NextToken")(x =>
      (x.resourceArn, x.nextToken)
    )
  given listTagsForResourceRequestCirceDecoder
      : circe.Decoder[ListTagsForResourceRequest] = x =>
    for
      resourceArn <- x.downField("ResourceARN").as[String]
      nextToken <- x.downField("NextToken").as[Option[String]]
    yield ListTagsForResourceRequest(resourceArn, nextToken)
  given listTagsForResourceRequestEncoder
      : Encoder[ListTagsForResourceRequest] = Encoder.derive
  given listTagsForResourceRequestDecoder
      : Decoder[ListTagsForResourceRequest] = Decoder.derive
  given Eq[ListTagsForResourceRequest] = Eq.fromUniversalEquals
