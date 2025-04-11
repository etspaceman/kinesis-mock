package kinesis.mock

import java.time.Instant

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import ciris.*
import enumeratum.*
import org.typelevel.log4cats.SelfAwareStructuredLogger

@SuppressWarnings(Array("scalafix:DisableSyntax.defaultArgs"))
final class ConsoleLogger[F[_]](
    logLevel: ConsoleLogger.LogLevel,
    loggerName: String
)(implicit
    C: Console[F],
    F: Async[F]
) extends SelfAwareStructuredLogger[F]:

  private def printMessage(
      logLevel: String,
      ctx: Map[String, String] = Map.empty,
      e: Option[Throwable] = None
  )(
      message: => String
  ): F[Unit] = for
    now <- F.realTime.map(d => Instant.EPOCH.plusNanos(d.toNanos))
    _ <- C.println(
      s"[$logLevel] $loggerName $now ${ctx.map { case (k, v) => s"$k=$v" }.mkString(", ")} $message"
    )
    _ <- e.fold(F.unit)(e => C.printStackTrace(e))
  yield ()

  override def error(message: => String): F[Unit] =
    isErrorEnabled.ifM(printMessage("error")(message), F.unit)

  override def warn(message: => String): F[Unit] =
    isWarnEnabled.ifM(printMessage("warn")(message), F.unit)

  override def info(message: => String): F[Unit] =
    isInfoEnabled.ifM(printMessage("info")(message), F.unit)

  override def debug(message: => String): F[Unit] =
    isDebugEnabled.ifM(printMessage("debug")(message), F.unit)

  override def trace(message: => String): F[Unit] =
    isTraceEnabled.ifM(printMessage("trace")(message), F.unit)

  override def error(t: Throwable)(message: => String): F[Unit] =
    isErrorEnabled.ifM(printMessage("error", e = Some(t))(message), F.unit)

  override def warn(t: Throwable)(message: => String): F[Unit] =
    isWarnEnabled.ifM(printMessage("warn", e = Some(t))(message), F.unit)

  override def info(t: Throwable)(message: => String): F[Unit] =
    isInfoEnabled.ifM(printMessage("info", e = Some(t))(message), F.unit)

  override def debug(t: Throwable)(message: => String): F[Unit] =
    isDebugEnabled.ifM(printMessage("debug", e = Some(t))(message), F.unit)

  override def trace(t: Throwable)(message: => String): F[Unit] =
    isTraceEnabled.ifM(printMessage("trace", e = Some(t))(message), F.unit)

  override def isTraceEnabled: F[Boolean] =
    F.pure(logLevel == ConsoleLogger.LogLevel.Trace)

  override def isDebugEnabled: F[Boolean] = F.pure(
    logLevel == ConsoleLogger.LogLevel.Trace ||
      logLevel == ConsoleLogger.LogLevel.Debug
  )

  override def isInfoEnabled: F[Boolean] = F.pure(
    logLevel == ConsoleLogger.LogLevel.Trace ||
      logLevel == ConsoleLogger.LogLevel.Debug ||
      logLevel == ConsoleLogger.LogLevel.Info
  )

  override def isWarnEnabled: F[Boolean] = F.pure(
    logLevel == ConsoleLogger.LogLevel.Trace ||
      logLevel == ConsoleLogger.LogLevel.Debug ||
      logLevel == ConsoleLogger.LogLevel.Info ||
      logLevel == ConsoleLogger.LogLevel.Warn
  )
  override def isErrorEnabled: F[Boolean] = F.pure(true)

  override def trace(ctx: Map[String, String])(msg: => String): F[Unit] =
    isTraceEnabled.ifM(printMessage("trace", ctx)(msg), F.unit)

  override def trace(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isTraceEnabled.ifM(printMessage("trace", ctx, Option(t))(msg), F.unit)

  override def debug(ctx: Map[String, String])(msg: => String): F[Unit] =
    isDebugEnabled.ifM(printMessage("debug", ctx)(msg), F.unit)

  override def debug(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isDebugEnabled.ifM(printMessage("debug", ctx, Option(t))(msg), F.unit)

  override def info(ctx: Map[String, String])(msg: => String): F[Unit] =
    isInfoEnabled.ifM(printMessage("info", ctx)(msg), F.unit)

  override def info(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isInfoEnabled.ifM(printMessage("info", ctx, Option(t))(msg), F.unit)

  override def warn(ctx: Map[String, String])(msg: => String): F[Unit] =
    isWarnEnabled.ifM(printMessage("warn", ctx)(msg), F.unit)

  override def warn(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isWarnEnabled.ifM(printMessage("warn", ctx, Option(t))(msg), F.unit)

  override def error(ctx: Map[String, String])(msg: => String): F[Unit] =
    isErrorEnabled.ifM(printMessage("error", ctx)(msg), F.unit)

  override def error(ctx: Map[String, String], t: Throwable)(
      msg: => String
  ): F[Unit] =
    isErrorEnabled.ifM(printMessage("error", ctx, Option(t))(msg), F.unit)

object ConsoleLogger:
  sealed trait LogLevel extends EnumEntry with EnumEntry.Uppercase
  object LogLevel
      extends Enum[LogLevel]
      with CirceEnum[LogLevel]
      with CirisEnum[LogLevel]:
    override val values = findValues

    case object Error extends LogLevel
    case object Warn extends LogLevel
    case object Info extends LogLevel
    case object Debug extends LogLevel
    case object Trace extends LogLevel

    def read: ConfigValue[Effect, LogLevel] =
      env("LOG_LEVEL").default("ERROR").as[LogLevel]
