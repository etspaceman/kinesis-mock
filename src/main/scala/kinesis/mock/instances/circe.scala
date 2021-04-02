package kinesis.mock.instances

import scala.concurrent.duration.FiniteDuration

import java.time.Instant

import io.circe.{Decoder, Encoder}

object circe {
  implicit val finiteDurationCirceEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  implicit val instantCirceEncoder: Encoder[Instant] =
    Encoder[Long].contramap(_.getEpochSecond())

  implicit val instantCirceDecoder: Decoder[Instant] =
    Decoder[Long].map(Instant.ofEpochSecond)
}
