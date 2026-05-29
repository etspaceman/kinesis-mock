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
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetResourcePolicy.html
final case class GetResourcePolicyRequest(
    resourceArn: ResourceArn
):
  def getResourcePolicy(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[GetResourcePolicyResponse]] =
    streamsRef.get.map { streams =>
      (resourceArn match
        case ResourceArn.Stream(streamArn) =>
          CommonValidations.findStream(streamArn, streams).void
        case ResourceArn.Consumer(consumerArn) =>
          CommonValidations
            .findStreamByConsumerArn(consumerArn, streams)
            .void
      ).flatMap(_ =>
        streams.resourcePolicies
          .get(resourceArn.resourceArn)
          .toRight(
            ResourceNotFoundException(
              s"No resource policy exists for ${resourceArn.resourceArn}"
            )
          )
      ).map(GetResourcePolicyResponse(_))
    }

object GetResourcePolicyRequest:
  given getResourcePolicyRequestCirceEncoder
      : circe.Encoder[GetResourcePolicyRequest] =
    circe.Encoder.forProduct1("ResourceARN")(_.resourceArn)
  given getResourcePolicyRequestCirceDecoder
      : circe.Decoder[GetResourcePolicyRequest] = x =>
    x.downField("ResourceARN")
      .as[ResourceArn]
      .map(GetResourcePolicyRequest(_))
  given getResourcePolicyRequestEncoder: Encoder[GetResourcePolicyRequest] =
    Encoder.derive
  given getResourcePolicyRequestDecoder: Decoder[GetResourcePolicyRequest] =
    Decoder.derive
  given Eq[GetResourcePolicyRequest] = (x, y) => x.resourceArn === y.resourceArn
