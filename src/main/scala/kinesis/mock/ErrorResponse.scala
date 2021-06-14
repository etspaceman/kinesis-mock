package kinesis.mock

import io.circe.derivation._

final case class ErrorResponse(__type: String, message: String)

object ErrorResponse {
  implicit val errorResponseEncoder: Encoder[ErrorResponse] =
    Encoder.derive(deriveEncoder)
  implicit val errorResponseDecoder: Decoder[ErrorResponse] =
    Decoder.derive(deriveDecoder)
}
