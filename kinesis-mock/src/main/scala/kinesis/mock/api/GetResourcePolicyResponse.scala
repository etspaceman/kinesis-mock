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
import io.circe

final case class GetResourcePolicyResponse(policy: String)

object GetResourcePolicyResponse:
  given getResourcePolicyResponseCirceEncoder
      : circe.Encoder[GetResourcePolicyResponse] =
    circe.Encoder.forProduct1("Policy")(_.policy)
  given getResourcePolicyResponseCirceDecoder
      : circe.Decoder[GetResourcePolicyResponse] = x =>
    x.downField("Policy").as[String].map(GetResourcePolicyResponse(_))
  given getResourcePolicyResponseEncoder: Encoder[GetResourcePolicyResponse] =
    Encoder.derive
  given getResourcePolicyResponseDecoder: Decoder[GetResourcePolicyResponse] =
    Decoder.derive
  given Eq[GetResourcePolicyResponse] = Eq.fromUniversalEquals
