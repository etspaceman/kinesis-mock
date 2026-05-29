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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutResourcePolicy.html
final case class PutResourcePolicyRequest(
    resourceArn: ResourceArn,
    policy: String
):
  def putResourcePolicy(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    (resourceArn match
      case ResourceArn.Stream(streamArn) =>
        CommonValidations.findStream(streamArn, streams).void
      case ResourceArn.Consumer(consumerArn) =>
        CommonValidations.findStreamByConsumerArn(consumerArn, streams).void
    ).map(_ =>
      streams.copy(resourcePolicies =
        streams.resourcePolicies.updated(resourceArn.resourceArn, policy)
      )
    ).map(updated => (updated, ()))
      .sequenceWithDefault(streams)
  }

object PutResourcePolicyRequest:
  given putResourcePolicyRequestCirceEncoder
      : circe.Encoder[PutResourcePolicyRequest] =
    circe.Encoder.forProduct2("ResourceARN", "Policy")(x =>
      (x.resourceArn, x.policy)
    )
  given putResourcePolicyRequestCirceDecoder
      : circe.Decoder[PutResourcePolicyRequest] = x =>
    for
      resourceArn <- x.downField("ResourceARN").as[ResourceArn]
      policy <- x.downField("Policy").as[String]
    yield PutResourcePolicyRequest(resourceArn, policy)
  given putResourcePolicyRequestEncoder: Encoder[PutResourcePolicyRequest] =
    Encoder.derive
  given putResourcePolicyRequestDecoder: Decoder[PutResourcePolicyRequest] =
    Decoder.derive
  given Eq[PutResourcePolicyRequest] = (x, y) =>
    x.resourceArn === y.resourceArn && x.policy === y.policy
