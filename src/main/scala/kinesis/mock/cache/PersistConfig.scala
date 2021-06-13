package kinesis.mock.cache

import scala.concurrent.duration.FiniteDuration

import io.circe.Encoder
import io.circe.derivation._
import os.Path
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

import kinesis.mock.instances.circe._
import kinesis.mock.instances.pureconfig._

final case class PersistConfig(
    loadIfExists: Boolean,
    shouldPersist: Boolean,
    path: Path,
    fileName: String,
    interval: FiniteDuration
)

object PersistConfig {
  implicit val persistConfigCirceEncoder: Encoder[PersistConfig] = deriveEncoder
  implicit val persistConfigConfigReader: ConfigReader[PersistConfig] =
    deriveReader
}
