package kinesis.mock

import cats.effect.IO
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{ConfigDecoder, ConfigSource}

final case class FunctionalTestConfig(servicePort: Int)

object FunctionalTestConfig {
  implicit val functionalTestConfigDecoder: ConfigDecoder[FunctionalTestConfig] =
    deriveReader

  def read: IO[FunctionalTestConfig] =
    ConfigSource.resources("test.conf").loadF[IO, FunctionalTestConfig]()
}
