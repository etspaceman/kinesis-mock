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

package kinesis.mock.retry

import scala.concurrent.duration.FiniteDuration

sealed trait RetryDetails:
  def retriesSoFar: Int
  def cumulativeDelay: FiniteDuration
  def givingUp: Boolean
  def upcomingDelay: Option[FiniteDuration]

object RetryDetails:
  final case class GivingUp(
      totalRetries: Int,
      totalDelay: FiniteDuration
  ) extends RetryDetails:
    val retriesSoFar: Int = totalRetries
    val cumulativeDelay: FiniteDuration = totalDelay
    val givingUp: Boolean = true
    val upcomingDelay: Option[FiniteDuration] = None

  final case class WillDelayAndRetry(
      nextDelay: FiniteDuration,
      retriesSoFar: Int,
      cumulativeDelay: FiniteDuration
  ) extends RetryDetails:
    val givingUp: Boolean = false
    val upcomingDelay: Option[FiniteDuration] = Some(nextDelay)
