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

import org.http4s.syntax.literals._
import org.http4s.{MediaRange, MediaType}

object KinesisMockMediaTypes {
  val amazonJson: MediaType = mediaType"application/x-amz-json-1.1"
  val amazonCbor: MediaType = mediaType"application/x-amz-cbor-1.1"
  val validContentTypes: Set[MediaRange] = Set(
    MediaType.application.json,
    amazonJson,
    amazonCbor
  )
}
