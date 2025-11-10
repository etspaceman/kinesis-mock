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

trait Decoder[A]:
  def circeDecoder: circe.Decoder[A]
  def circeCborDecoder: circe.Decoder[A]
  lazy val borerDecoder: borer.Decoder[A] =
    kinesis.mock.instances.borer
      .defaultBorerDecoderFromCirceDecoder(using circeDecoder)

object Decoder:
  def apply[A](using D: Decoder[A]): Decoder[A] = D
  def instance[A](
      circeDecoder0: circe.Decoder[A],
      circeCborDecoder0: circe.Decoder[A]
  ): Decoder[A] = new Decoder[A]:
    override val circeDecoder: circe.Decoder[A] = circeDecoder0
    override val circeCborDecoder: circe.Decoder[A] = circeCborDecoder0
  def derive[A](using circeDecoder0: circe.Decoder[A]): Decoder[A] =
    instance(circeDecoder0, circeDecoder0)
