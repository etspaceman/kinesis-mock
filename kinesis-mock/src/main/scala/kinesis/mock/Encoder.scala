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

import io.bullet.borer
import io.circe

trait Encoder[A]:
  def circeEncoder: circe.Encoder[A]
  def circeCborEncoder: circe.Encoder[A]
  lazy val borerEncoder: borer.Encoder[A] =
    kinesis.mock.instances.borer.borerEncoderFromCirceEncoder(circeCborEncoder)

object Encoder:
  def apply[A](implicit E: Encoder[A]): Encoder[A] = E
  def instance[A](
      circeEncoder0: circe.Encoder[A],
      circeCborEncoder0: circe.Encoder[A]
  ): Encoder[A] = new Encoder[A]:
    override val circeEncoder: circe.Encoder[A] = circeEncoder0
    override val circeCborEncoder: circe.Encoder[A] = circeCborEncoder0
  def derive[A](implicit circeEncoder0: circe.Encoder[A]): Encoder[A] =
    instance(circeEncoder0, circeEncoder0)
