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
package models

import cats.Eq
import io.circe

final case class StreamModeDetails(
    streamMode: StreamMode
)

object StreamModeDetails {
  implicit val streamModeDetailsCirceEncoder: circe.Encoder[StreamModeDetails] =
    circe.Encoder.forProduct1(
      "StreamMode"
    )(x => x.streamMode)

  implicit val streamModeDetailsCirceDecoder: circe.Decoder[StreamModeDetails] =
    x =>
      for {
        streamMode <- x.downField("StreamMode").as[StreamMode]
      } yield StreamModeDetails(streamMode)

  implicit val streamModeDetailsEncoder: Encoder[StreamModeDetails] =
    Encoder.derive
  implicit val streamModeDetailsDecoder: Decoder[StreamModeDetails] =
    Decoder.derive

  implicit val streamModeDetailsEq: Eq[StreamModeDetails] =
    Eq.fromUniversalEquals
}
