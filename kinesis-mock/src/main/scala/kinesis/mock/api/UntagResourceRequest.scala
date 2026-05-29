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
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UntagResource.html
final case class UntagResourceRequest(
    resourceArn: String,
    tagKeys: List[String]
):
  def untagResource(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    ResourceArn
      .fromString(resourceArn)
      .flatMap {
        case ResourceArn.Stream(streamArn) =>
          CommonValidations
            .findStream(streamArn, streams)
            .map(stream =>
              streams.updateStream(
                stream.copy(tags = stream.tags -- tagKeys)
              )
            )
        case ResourceArn.Consumer(consumerArn) =>
          CommonValidations
            .findStreamByConsumerArn(consumerArn, streams)
            .map { case (consumer, stream) =>
              streams.updateStream(
                stream.copy(consumers =
                  stream.consumers ++ Seq(
                    consumer.consumerName ->
                      consumer.copy(tags = consumer.tags -- tagKeys)
                  )
                )
              )
            }
      }
      .map(updated => (updated, ()))
      .sequenceWithDefault(streams)
  }

object UntagResourceRequest:
  given untagResourceRequestCirceEncoder: circe.Encoder[UntagResourceRequest] =
    circe.Encoder.forProduct2("ResourceARN", "TagKeys")(x =>
      (x.resourceArn, x.tagKeys)
    )
  given untagResourceRequestCirceDecoder: circe.Decoder[UntagResourceRequest] =
    x =>
      for
        resourceArn <- x.downField("ResourceARN").as[String]
        tagKeys <- x.downField("TagKeys").as[List[String]]
      yield UntagResourceRequest(resourceArn, tagKeys)
  given untagResourceRequestEncoder: Encoder[UntagResourceRequest] =
    Encoder.derive
  given untagResourceRequestDecoder: Decoder[UntagResourceRequest] =
    Decoder.derive
  given Eq[UntagResourceRequest] = Eq.fromUniversalEquals
