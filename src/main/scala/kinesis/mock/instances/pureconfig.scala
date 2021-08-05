package kinesis.mock.instances

import _root_.pureconfig.ConfigReader
import com.comcast.ip4s.Port

object pureconfig {
  implicit val portConfigReader: ConfigReader[Port] =
    ConfigReader.fromStringOpt(Port.fromString)
}
