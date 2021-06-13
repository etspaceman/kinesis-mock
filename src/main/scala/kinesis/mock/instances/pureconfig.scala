package kinesis.mock.instances

import scala.util.Try

import _root_.pureconfig.ConfigReader
import os.Path

object pureconfig {
  implicit val pathConfigReader: ConfigReader[Path] =
    ConfigReader.fromStringTry(x => Try(Path(x)))
}
