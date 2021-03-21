package kinesis.mock.models

import cats.kernel.Eq
import io.circe._

final case class StreamName(streamName: String) {
  override def toString: String = streamName
}

object StreamName {
  implicit val streamNameCirceEncoder: Encoder[StreamName] =
    Encoder[String].contramap(_.streamName)
  implicit val streamNameCirceDecoder: Decoder[StreamName] =
    Decoder[String].map(StreamName.apply)
  implicit val streamNameEq: Eq[StreamName] = Eq.fromUniversalEquals
  implicit val streamNameOrdering: Ordering[StreamName] =
    new Ordering[StreamName] {
      override def compare(x: StreamName, y: StreamName): Int =
        Ordering[String].compare(x.streamName, y.streamName)
    }
}
