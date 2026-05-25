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
