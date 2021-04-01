package kinesis.mock.instances

import scala.concurrent.duration.FiniteDuration

import io.circe.Encoder

object circe {
  implicit val finiteDurationCirceEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)
}
