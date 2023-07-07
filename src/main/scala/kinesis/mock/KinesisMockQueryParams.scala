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

import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object KinesisMockQueryParams {
  val amazonAuthAlgorithm = "X-Amz-Algorithm"
  val amazonAuthCredential = "X-Amz-Credential"
  val amazonAuthSignature = "X-Amz-Signature"
  val amazonAuthSignedHeaders = "X-Amz-SignedHeaders"
  val amazonDateQuery = "X-Amz-Date"
  val amazonAction = "Action"

  object AmazonAuthAlgorithm
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthAlgorithm)

  object AmazonAuthCredential
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthCredential)

  object AmazonAuthSignature
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthSignature)

  object AmazonAuthSignedHeaders
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthSignedHeaders)

  object AmazonDate
      extends OptionalQueryParamDecoderMatcher[String](amazonDateQuery)

  object Action
      extends OptionalQueryParamDecoderMatcher[KinesisAction](amazonAction)
}
