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

import java.time.Instant

import cats.effect.IO
import cats.effect.std.SecureRandom
import cats.effect.std.UUIDGen

object Utils:
  private def getUUIDGen: IO[UUIDGen[IO]] = SecureRandom
    .javaSecuritySecureRandom[IO]
    .map(x => UUIDGen.fromSecureRandom[IO](implicitly, x))

  def randomUUID = getUUIDGen.flatMap(x => x.randomUUID)
  def randomUUIDString = randomUUID.map(_.toString)

  def md5(bytes: Array[Byte]): Array[Byte] = MD5.compute(bytes)
  def now: IO[Instant] =
    IO.realTime.map(d => Instant.EPOCH.plusNanos(d.toNanos))
