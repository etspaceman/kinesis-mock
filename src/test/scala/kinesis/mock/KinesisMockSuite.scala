package kinesis.mock

import scala.concurrent.ExecutionContext

import java.util.concurrent.{
  ScheduledExecutorService,
  ScheduledThreadPoolExecutor,
  TimeUnit
}

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import munit._

import threads.{newBlockingPool, newDaemonPool, threadFactory}

// Shamelessly stolen from Http4s's test suite: https://github.com/http4s/http4s/blob/main/testing/src/test/scala/org/http4s/Http4sSuite.scala
trait KinesisMockSuite
    extends CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  override val munitExecutionContext =
    ExecutionContext.fromExecutor(
      newDaemonPool("http4s-munit", timeout = true)
    )

  override implicit val ioRuntime: IORuntime = KinesisMockSuite.TestIORuntime
}

object KinesisMockSuite {
  val TestExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(
      newDaemonPool("kinesis-mock-suite", timeout = true)
    )

  val TestScheduler: ScheduledExecutorService = {
    val s =
      new ScheduledThreadPoolExecutor(
        2,
        threadFactory(i => s"kinesis-mock-test-scheduler-$i", true)
      )
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestIORuntime: IORuntime = {
    val blockingPool = newBlockingPool("kinesis-mock-suite-blocking")
    val computePool = newDaemonPool("kinesis-mock-suite", timeout = true)
    val scheduledExecutor = TestScheduler
    IORuntime.apply(
      ExecutionContext.fromExecutor(computePool),
      ExecutionContext.fromExecutor(blockingPool),
      Scheduler.fromScheduledExecutor(scheduledExecutor),
      () => {
        blockingPool.shutdown()
        computePool.shutdown()
        scheduledExecutor.shutdown()
      },
      IORuntimeConfig()
    )
  }
}
