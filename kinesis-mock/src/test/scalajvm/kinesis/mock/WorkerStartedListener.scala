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

import cats.effect.unsafe.IORuntime
import cats.effect.{Deferred, IO}
import software.amazon.kinesis.coordinator.WorkerStateChangeListener
import software.amazon.kinesis.coordinator.WorkerStateChangeListener.WorkerState

final case class WorkerStartedListener(started: Deferred[IO, Unit])(using
    R: IORuntime
) extends WorkerStateChangeListener:
  override def onWorkerStateChange(newState: WorkerState): Unit =
    if newState == WorkerState.STARTED then

      val _ = started.complete(()).unsafeRunSync()
  override def onAllInitializationAttemptsFailed(e: Throwable): Unit =
    throw e // scalafix:ok
