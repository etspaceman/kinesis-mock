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
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_TagResource.html
final case class TagResourceRequest(
    resourceArn: String,
    tags: Tags
):
  def tagResource(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    ResourceArn
      .fromString(resourceArn)
      .flatMap {
        case ResourceArn.Stream(streamArn) =>
          CommonValidations
            .findStream(streamArn, streams)
            .flatMap(stream =>
              CommonValidations
                .validateTags(tags, stream.tags)
                .map(_ =>
                  streams.updateStream(
                    stream.copy(tags = stream.tags |+| tags)
                  )
                )
            )
        case ResourceArn.Consumer(consumerArn) =>
          CommonValidations
            .findStreamByConsumerArn(consumerArn, streams)
            .flatMap { case (consumer, stream) =>
              CommonValidations
                .validateTags(tags, consumer.tags)
                .map(_ =>
                  streams.updateStream(
                    stream.copy(consumers =
                      stream.consumers ++ Seq(
                        consumer.consumerName ->
                          consumer.copy(tags = consumer.tags |+| tags)
                      )
                    )
                  )
                )
            }
      }
      .map(updated => (updated, ()))
      .sequenceWithDefault(streams)
  }

object TagResourceRequest:
  given tagResourceRequestCirceEncoder: circe.Encoder[TagResourceRequest] =
    circe.Encoder.forProduct2("ResourceARN", "Tags")(x =>
      (x.resourceArn, x.tags)
    )
  given tagResourceRequestCirceDecoder: circe.Decoder[TagResourceRequest] = x =>
    for
      resourceArn <- x.downField("ResourceARN").as[String]
      tags <- x.downField("Tags").as[Tags]
    yield TagResourceRequest(resourceArn, tags)
  given tagResourceRequestEncoder: Encoder[TagResourceRequest] = Encoder.derive
  given tagResourceRequestDecoder: Decoder[TagResourceRequest] = Decoder.derive
  given Eq[TagResourceRequest] = Eq.fromUniversalEquals
