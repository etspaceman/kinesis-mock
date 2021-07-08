package kinesis.mock

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

// Shamelessly stolen from Http4s's thread setup: https://github.com/http4s/http4s/blob/main/core/src/main/scala/org/http4s/internal/threads.scala
@SuppressWarnings(Array("scalafix:DisableSyntax.defaultArgs"))
object threads {
  final case class ThreadPriority(toInt: Int)
  case object ThreadPriority {
    val Min = ThreadPriority(Thread.MIN_PRIORITY)
    val Norm = ThreadPriority(Thread.NORM_PRIORITY)
    val Max = ThreadPriority(Thread.MAX_PRIORITY)
  }

  def threadFactory(
      name: Long => String = { l =>
        s"kinesis-mock-$l"
      },
      daemon: Boolean = false,
      priority: ThreadPriority = ThreadPriority.Norm,
      uncaughtExceptionHandler: PartialFunction[(Thread, Throwable), Unit] =
        PartialFunction.empty,
      backingThreadFactory: ThreadFactory = Executors.defaultThreadFactory
  ): ThreadFactory =
    new ThreadFactory {
      val count = new AtomicLong(0)
      override def newThread(r: Runnable): Thread = {
        val thread = backingThreadFactory.newThread(r)
        thread.setName(name(count.getAndIncrement))
        thread.setDaemon(daemon)
        thread.setPriority(priority.toInt)
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
          override def uncaughtException(t: Thread, e: Throwable): Unit =
            uncaughtExceptionHandler.orElse(fallthrough)((t, e))
          val fallthrough: PartialFunction[(Thread, Throwable), Unit] = {
            case (t: Thread, e: Throwable) =>
              Option(t.getThreadGroup)
                .getOrElse(Thread.getDefaultUncaughtExceptionHandler)
                .uncaughtException(t, e)
          }
        })
        thread
      }
    }

  def newDaemonPool(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false
  ): ThreadPoolExecutor = {
    val cpus = Runtime.getRuntime.availableProcessors
    val exec = new ThreadPoolExecutor(
      math.max(min, cpus),
      math.max(min, (cpus * cpuFactor).ceil.toInt),
      10L,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory(i => s"$name-$i", daemon = true)
    )
    exec.allowCoreThreadTimeOut(timeout)
    exec
  }

  def newDaemonPoolExecutionContext(
      name: String,
      min: Int = 4,
      cpuFactor: Double = 3.0,
      timeout: Boolean = false
  ): ExecutionContext =
    ExecutionContext.fromExecutorService(
      newDaemonPool(name, min, cpuFactor, timeout)
    )

  def newBlockingPool(name: String): ExecutorService = {
    val CorePoolSize = 0
    val MaxPoolSize = Int.MaxValue
    val KeepAliveTime = 1.minute

    new ThreadPoolExecutor(
      CorePoolSize,
      MaxPoolSize,
      KeepAliveTime.toSeconds,
      TimeUnit.SECONDS,
      new SynchronousQueue[Runnable](false),
      threadFactory(i => s"$name-$i", daemon = true)
    )
  }
}
