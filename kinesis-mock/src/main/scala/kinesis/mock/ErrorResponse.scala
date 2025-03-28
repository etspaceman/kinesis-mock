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

final case class ErrorResponse(__type: String, message: String)

object ErrorResponse:
  given errorResponseCirceEncoder: io.circe.Encoder[ErrorResponse] =
    io.circe.Encoder.forProduct2("__type", "message")(x =>
      (x.__type, x.message)
    )
  given errorResponseCirceDecoder: io.circe.Decoder[ErrorResponse] = x =>
    for
      __type <- x.downField("__type").as[String]
      message <- x.downField("message").as[String]
    yield ErrorResponse(__type, message)
  given errorResponseEncoder: Encoder[ErrorResponse] =
    Encoder.derive
  given errorResponseDecoder: Decoder[ErrorResponse] =
    Decoder.derive
