package kinesis.mock

import cats.effect.{Blocker, ContextShift, IO}
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{ConfigReader, ConfigSource}

final case class KinesisMockServiceConfig(
    http2Port: Int,
    http1SslPort: Int,
    http1PlainPort: Int,
    keyStorePassword: String,
    keyManagerPassword: String
)

object KinesisMockServiceConfig {
  implicit val kinesisMockServiceConfigReader
      : ConfigReader[KinesisMockServiceConfig] = deriveReader
  def read(
      blocker: Blocker
  )(implicit CS: ContextShift[IO]): IO[KinesisMockServiceConfig] =
    ConfigSource
      .resources("service.conf")
      .loadF[IO, KinesisMockServiceConfig](blocker)
}
