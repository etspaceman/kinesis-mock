package kinesis.mock

final case class ErrorResponse(__type: String, message: String)

object ErrorResponse {
  implicit val errorResponseCirceEncoder: io.circe.Encoder[ErrorResponse] =
    io.circe.Encoder.forProduct2("__type", "message")(x =>
      (x.__type, x.message)
    )
  implicit val errorResponseCirceDecoder: io.circe.Decoder[ErrorResponse] = {
    x =>
      for {
        __type <- x.downField("__type").as[String]
        message <- x.downField("message").as[String]
      } yield ErrorResponse(__type, message)
  }
  implicit val errorResponseEncoder: Encoder[ErrorResponse] =
    Encoder.derive
  implicit val errorResponseDecoder: Decoder[ErrorResponse] =
    Decoder.derive

}
