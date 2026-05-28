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
package models

import cats.Eq
import cats.syntax.all.*

enum ResourceArn(val resourceArn: String):
  case Stream(streamArn: StreamArn) extends ResourceArn(streamArn.streamArn)
  case Consumer(consumerArn: ConsumerArn)
      extends ResourceArn(consumerArn.consumerArn)

  override def toString: String = resourceArn

object ResourceArn:
  def fromString(s: String): Either[KinesisMockException, ResourceArn] =
    if !s.startsWith("arn:") || !s.contains(":stream/") then
      InvalidArgumentException(
        s"Resource ARN $s is not a recognized Kinesis stream or consumer ARN"
      ).asLeft
    else if s.contains("/consumer/") then
      ConsumerArn
        .fromArn(s)
        .bimap(
          msg =>
            InvalidArgumentException(
              s"Resource ARN $s is not a recognized Kinesis consumer ARN: $msg"
            ),
          ResourceArn.Consumer(_)
        )
    else
      StreamArn
        .fromArn(s)
        .bimap(
          msg =>
            InvalidArgumentException(
              s"Resource ARN $s is not a recognized Kinesis stream or consumer ARN: $msg"
            ),
          ResourceArn.Stream(_)
        )

  given Eq[ResourceArn] = Eq.fromUniversalEquals
