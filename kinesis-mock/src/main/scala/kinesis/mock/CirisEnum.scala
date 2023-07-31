package kinesis.mock

import cats.syntax.all._
import ciris._
import enumeratum.{Enum, EnumEntry}

trait CirisEnum[A <: EnumEntry] { self: Enum[A] =>
  implicit val configDecoderInstance: ConfigDecoder[String, A] =
    ConfigDecoder[String].mapEither { case (_, x) =>
      self.withNameEither(x).leftMap(e => ConfigError(e.getMessage))
    }
}
