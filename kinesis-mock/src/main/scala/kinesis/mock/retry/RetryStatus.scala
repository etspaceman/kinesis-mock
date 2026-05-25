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

import scala.concurrent.duration.{Duration, FiniteDuration}

final case class RetryStatus(
    retriesSoFar: Int,
    cumulativeDelay: FiniteDuration,
    previousDelay: Option[FiniteDuration]
):
  def addRetry(delay: FiniteDuration): RetryStatus = RetryStatus(
    retriesSoFar = this.retriesSoFar + 1,
    cumulativeDelay = this.cumulativeDelay + delay,
    previousDelay = Some(delay)
  )

object RetryStatus:
  val NoRetriesYet: RetryStatus = RetryStatus(0, Duration.Zero, None)
