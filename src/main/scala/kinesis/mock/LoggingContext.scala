package kinesis.mock

import com.github.f4b6a3.uuid.UuidCreator
import io.circe.Json

final case class LoggingContext private (context: Map[String, String]) {
  def +(kv: (String, String)): LoggingContext = copy(context + kv)
  def ++(kvs: IterableOnce[(String, String)]): LoggingContext = copy(
    context ++ kvs
  )
  def addJson(key: String, js: Json): LoggingContext = copy(
    context + (key -> js.noSpacesSortKeys)
  )
}

object LoggingContext {
  def create: LoggingContext = LoggingContext(
    Map("contextId" -> UuidCreator.toString(UuidCreator.getTimeBased()))
  )
}
