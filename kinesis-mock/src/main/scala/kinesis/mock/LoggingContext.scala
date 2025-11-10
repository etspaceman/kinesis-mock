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

import cats.effect.IO
import io.circe.Json

final case class LoggingContext private (context: Map[String, String]):
  def +(kv: (String, String)): LoggingContext = copy(context + kv)
  def ++(kvs: IterableOnce[(String, String)]): LoggingContext = copy(
    context ++ kvs
  )
  def addJson(key: String, js: Json): LoggingContext = copy(
    context + (key -> js.noSpacesSortKeys)
  )
  def addEncoded[A: Encoder](
      key: String,
      a: A,
      isCbor: Boolean
  ): LoggingContext =
    addJson(
      key,
      if isCbor then Encoder[A].circeCborEncoder(a)
      else Encoder[A].circeEncoder(a)
    )

object LoggingContext:
  def create: IO[LoggingContext] = Utils.randomUUIDString.map(uuid =>
    LoggingContext(
      Map("contextId" -> uuid)
    )
  )
