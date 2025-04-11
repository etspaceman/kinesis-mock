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

package kinesis.mock.models

import cats.Eq
import io.circe.*

final case class StreamName(streamName: String):
  override def toString: String = streamName

object StreamName:
  given streamNameCirceEncoder: Encoder[StreamName] =
    Encoder[String].contramap(_.streamName)
  given streamNameCirceDecoder: Decoder[StreamName] =
    Decoder[String].map(StreamName.apply)
  given streamNameCirceKeyEncoder: KeyEncoder[StreamName] =
    KeyEncoder[String].contramap(_.streamName)
  given streamNameCirceKeyDecoder: KeyDecoder[StreamName] =
    KeyDecoder[String].map(StreamName.apply)
  given streamNameEq: Eq[StreamName] = Eq.fromUniversalEquals
  given streamNameOrdering: Ordering[StreamName] =
    (x: StreamName, y: StreamName) =>
      Ordering[String].compare(x.streamName, y.streamName)
