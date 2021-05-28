package kinesis.mock
package models

import cats.Eq
import io.circe

final case class TagListEntry(key: String, value: String)

object TagListEntry {
  implicit val tagListEntryCirceEncoder: circe.Encoder[TagListEntry] =
    circe.Encoder.forProduct2("Key", "Value")(x => (x.key, x.value))
  implicit val tagListEntryCirceDecoder: circe.Decoder[TagListEntry] = x =>
    for {
      key <- x.downField("Key").as[String]
      value <- x.downField("Value").as[String]
    } yield TagListEntry(key, value)

  implicit val tagListEntryEncoder: Encoder[TagListEntry] = Encoder.derive
  implicit val tagListEntryDecoder: Decoder[TagListEntry] = Decoder.derive
  implicit val tagListEntryEq: Eq[TagListEntry] = Eq.fromUniversalEquals
}
