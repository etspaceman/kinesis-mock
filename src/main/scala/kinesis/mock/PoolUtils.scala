package kinesis.mock

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{
  Executors,
  ScheduledThreadPoolExecutor,
  ThreadFactory
}

import cats.effect.unsafe.Scheduler

object PoolUtils {

  val computeContext: ExecutionContext = {
    // lower-bound of 2 to prevent pathological deadlocks on virtual machines
    val bound = math.max(2, Runtime.getRuntime().availableProcessors())

    val executor = Executors.newFixedThreadPool(
      bound,
      new ThreadFactory {
        val ctr = new AtomicInteger(0)
        def newThread(r: Runnable): Thread = {
          val back =
            new Thread(r, s"kinesis-mock-compute-${ctr.getAndIncrement()}")
          back.setDaemon(true)
          back
        }
      }
    )

    exitOnFatal(ExecutionContext.fromExecutor(executor))
  }

  val blockingContext: ExecutionContext = {
    val threadCount = new AtomicInteger(0)
    val executor = Executors.newCachedThreadPool { (r: Runnable) =>
      val t = new Thread(r)
      t.setName(s"kinesis-mock-blocking-${threadCount.getAndIncrement()}")
      t.setDaemon(true)
      t
    }
    ExecutionContext.fromExecutor(executor)
  }

  val scheduler: Scheduler = {
    val s = new ScheduledThreadPoolExecutor(
      1,
      { r =>
        val t = new Thread(r)
        t.setName("kinesis-mock-scheduler")
        t.setDaemon(true)
        t.setPriority(Thread.MAX_PRIORITY)
        t
      }
    )
    s.setRemoveOnCancelPolicy(true)
    Scheduler.fromScheduledExecutor(s)
  }

  def exitOnFatal(ec: ExecutionContext): ExecutionContext =
    new ExecutionContext {
      def execute(r: Runnable): Unit =
        ec.execute(new Runnable {
          def run(): Unit =
            try {
              r.run()
            } catch {
              case NonFatal(t) =>
                reportFailure(t)

              case t: Throwable =>
                // under most circumstances, this will work even with fatal errors
                t.printStackTrace()
                System.exit(1)
            }
        })

      def reportFailure(t: Throwable): Unit =
        ec.reportFailure(t)
    }
}
