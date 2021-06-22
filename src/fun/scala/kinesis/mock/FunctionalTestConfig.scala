package kinesis.mock

import cats.effect.IO
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{ConfigReader, ConfigSource}

final case class FunctionalTestConfig(servicePort: Int)

object FunctionalTestConfig {
  implicit val functionalTestConfigReader: ConfigReader[FunctionalTestConfig] =
    deriveReader

  def read: IO[FunctionalTestConfig] =
    ConfigSource.resources("test.conf").loadF[IO, FunctionalTestConfig]()
}
