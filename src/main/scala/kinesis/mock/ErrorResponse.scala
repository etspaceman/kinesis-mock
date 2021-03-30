package kinesis.mock

import io.circe.derivation._
import io.circe.{Decoder, Encoder}

final case class ErrorResponse(__type: String, message: String)

object ErrorResponse {
  implicit val errorResponseCirceEncoder: Encoder[ErrorResponse] =
    deriveEncoder
  implicit val errorResponseCirceDecoder: Decoder[ErrorResponse] =
    deriveDecoder
}
