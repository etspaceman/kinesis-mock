package kinesis.mock.instances

import _root_.ciris._
import com.comcast.ip4s.Port

object ciris {
  implicit val portConfigDecoder: ConfigDecoder[String, Port] =
    ConfigDecoder[String].mapOption("Port")(Port.fromString)
}
