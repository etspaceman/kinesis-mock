package kinesis.mock.instances

import scala.concurrent.duration.FiniteDuration

import java.time.Instant
import java.util.Base64

import io.circe.{Decoder, Encoder}

object circe {
  implicit val finiteDurationCirceEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  implicit val instantCirceEncoder: Encoder[Instant] =
    Encoder[Long].contramap(_.getEpochSecond())

  implicit val instantCirceDecoder: Decoder[Instant] =
    Decoder[Long].map(Instant.ofEpochSecond)

  implicit val arrayBytesCirceEncoder: Encoder[Array[Byte]] =
    Encoder[String].contramap(str =>
      new String(Base64.getEncoder.encode(str), "UTF-8")
    )

  implicit val arrayBytesCirceDecoder: Decoder[Array[Byte]] =
    Decoder[String].map(str => Base64.getDecoder.decode(str))
}
