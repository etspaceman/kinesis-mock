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

package kinesis.mock.cache

import scala.concurrent.duration.FiniteDuration

import ciris._
import fs2.io.file.Path
import io.circe.Encoder

import kinesis.mock.instances.circe.given

final case class PersistConfig(
    loadIfExists: Boolean,
    shouldPersist: Boolean,
    path: String,
    fileName: String,
    interval: FiniteDuration
) {

  private def createPath(starting: Path, p: String): Path = {
    val split = p.split("/").toList
    split match {
      case Nil      => starting
      case h :: Nil => starting / h
      case h :: t   => t.foldLeft(starting / h) { case (acc, x) => acc / x }
    }
  }

  def osPath: Path = if (path.isEmpty) Path(".")
  else {
    if (!path.startsWith("/")) {
      createPath(Path("."), path)
    } else {
      createPath(Path("/"), path.drop(1))
    }
  }
  def osFile: Path = osPath / fileName
}

object PersistConfig {
  given persistConfigCirceEncoder: Encoder[PersistConfig] =
    Encoder.forProduct5(
      "loadIfExists",
      "shouldPersist",
      "path",
      "fileName",
      "interval"
    )(x => (x.loadIfExists, x.shouldPersist, x.path, x.fileName, x.interval))

  def read: ConfigValue[Effect, PersistConfig] = for {
    loadIfExists <- env("LOAD_DATA_IF_EXISTS").default("true").as[Boolean]
    shouldPersist <- env("SHOULD_PERSIST_DATA").default("false").as[Boolean]
    path <- env("PERSIST_PATH").default("data")
    fileName <- env("PERSIST_FILE_NAME").default("kinesis-data.json")
    interval <- env("PERSIST_INTERVAL").default("5s").as[FiniteDuration]
  } yield PersistConfig(loadIfExists, shouldPersist, path, fileName, interval)
}
