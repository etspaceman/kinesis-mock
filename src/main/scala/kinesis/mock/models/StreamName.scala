package kinesis.mock.models

import cats.Eq
import io.circe._

final case class StreamName(streamName: String) {
  override def toString: String = streamName
}

object StreamName {
  implicit val streamNameCirceEncoder: Encoder[StreamName] =
    Encoder[String].contramap(_.streamName)
  implicit val streamNameCirceDecoder: Decoder[StreamName] =
    Decoder[String].map(StreamName.apply)
  implicit val streamNameCirceKeyEncoder: KeyEncoder[StreamName] =
    KeyEncoder[String].contramap(_.streamName)
  implicit val streamNameCirceKeyDecoder: KeyDecoder[StreamName] =
    KeyDecoder[String].map(StreamName.apply)
  implicit val streamNameEq: Eq[StreamName] = Eq.fromUniversalEquals
  implicit val streamNameOrdering: Ordering[StreamName] =
    (x: StreamName, y: StreamName) =>
      Ordering[String].compare(x.streamName, y.streamName)
}
