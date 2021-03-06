package kinesis.mock

import com.github.f4b6a3.uuid.UuidCreator
import io.circe.Json

// $COVERAGE-OFF$
final case class LoggingContext private (context: Map[String, String]) {
  def +(kv: (String, String)): LoggingContext = copy(context + kv)
  def ++(kvs: IterableOnce[(String, String)]): LoggingContext = copy(
    context ++ kvs
  )
  def addJson(key: String, js: Json): LoggingContext = copy(
    context + (key -> js.noSpacesSortKeys)
  )
  def addEncoded[A: Encoder](
      key: String,
      a: A,
      isCbor: Boolean
  ): LoggingContext =
    addJson(
      key,
      if (isCbor) Encoder[A].circeCborEncoder(a) else Encoder[A].circeEncoder(a)
    )
}

object LoggingContext {
  def create: LoggingContext = LoggingContext(
    Map("contextId" -> UuidCreator.toString(UuidCreator.getTimeBased()))
  )
}
// $COVERAGE-ON$
