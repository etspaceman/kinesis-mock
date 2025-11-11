package kinesis.mock.instances

import _root_.ciris.*
import com.comcast.ip4s.Port

object ciris:
  given ConfigDecoder[String, Port] =
    ConfigDecoder[String].mapOption("Port")(Port.fromString)
