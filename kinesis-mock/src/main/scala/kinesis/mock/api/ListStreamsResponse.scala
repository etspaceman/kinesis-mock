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
import io.circe

import kinesis.mock.models.StreamName

final case class ListStreamsResponse(
    hasMoreStreams: Boolean,
    streamNames: Vector[StreamName]
)

object ListStreamsResponse:
  given listStreamsResponseCirceEncoder: circe.Encoder[ListStreamsResponse] =
    circe.Encoder.forProduct2("HasMoreStreams", "StreamNames")(x =>
      (x.hasMoreStreams, x.streamNames)
    )

  given listStreamsResponseCirceDecoder: circe.Decoder[ListStreamsResponse] =
    x =>
      for
        hasMoreStreams <- x.downField("HasMoreStreams").as[Boolean]
        streamNames <- x.downField("StreamNames").as[Vector[StreamName]]
      yield ListStreamsResponse(hasMoreStreams, streamNames)
  given listStreamsResponseEncoder: Encoder[ListStreamsResponse] =
    Encoder.derive
  given listStreamsResponseDecoder: Decoder[ListStreamsResponse] =
    Decoder.derive
  given listStreamsResponseEq: Eq[ListStreamsResponse] =
    Eq.fromUniversalEquals
