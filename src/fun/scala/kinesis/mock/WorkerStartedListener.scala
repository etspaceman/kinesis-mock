package kinesis.mock

import cats.effect.IO
import software.amazon.kinesis.coordinator.WorkerStateChangeListener
import software.amazon.kinesis.coordinator.WorkerStateChangeListener.WorkerState
import cats.effect.Deferred

final case class WorkerStartedListener(started: Deferred[IO, Unit])
    extends WorkerStateChangeListener {
  override def onWorkerStateChange(newState: WorkerState): Unit = {
    if (newState == WorkerState.STARTED) {
      started.complete(()).unsafeRunSync()
    }
  }
  override def onAllInitializationAttemptsFailed(e: Throwable): Unit =
    throw e // scalafix:ok
}
