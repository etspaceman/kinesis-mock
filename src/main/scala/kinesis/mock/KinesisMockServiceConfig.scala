package kinesis.mock

import cats.effect.IO
import com.comcast.ip4s.Port
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{ConfigReader, ConfigSource}

import kinesis.mock.instances.pureconfig._

final case class KinesisMockServiceConfig(
    tlsPort: Port,
    plainPort: Port,
    keyStorePassword: String,
    keyManagerPassword: String
)

object KinesisMockServiceConfig {
  implicit val kinesisMockServiceConfigReader
      : ConfigReader[KinesisMockServiceConfig] = deriveReader
  def read: IO[KinesisMockServiceConfig] =
    ConfigSource
      .resources("service.conf")
      .loadF[IO, KinesisMockServiceConfig]()
}
