package kinesis.mock.models

import cats.Eq
import io.circe._

final case class TagListEntry(key: String, value: String)

object TagListEntry {
  implicit val tagListEntryCirceEncoder: Encoder[TagListEntry] =
    Encoder.forProduct2("Key", "Value")(x => (x.key, x.value))
  implicit val tagListEntryCirceDecoder: Decoder[TagListEntry] = x =>
    for {
      key <- x.downField("Key").as[String]
      value <- x.downField("Value").as[String]
    } yield TagListEntry(key, value)

  implicit val tagListEntryEq: Eq[TagListEntry] = Eq.fromUniversalEquals
}
