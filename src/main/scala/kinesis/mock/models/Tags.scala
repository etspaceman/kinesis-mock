package kinesis.mock.models

import cats.{Eq, Monoid}
import io.circe._

final case class Tags(tags: Map[String, String]) {
  def size: Int = tags.size
  def --(keys: IterableOnce[String]): Tags = copy(tags = tags -- keys)
  override def toString(): String = tags.toString()
  def toList: List[(String, String)] = tags.toList
}

object Tags {
  def empty: Tags = Tags(Map.empty)
  implicit val tagsCirceEncoder: Encoder[Tags] =
    Encoder[Map[String, String]].contramap(_.tags)
  implicit val tagsCirceDecoder: Decoder[Tags] =
    Decoder[Map[String, String]].map(Tags.apply)
  implicit val tagsEq: Eq[Tags] = Eq.fromUniversalEquals
  implicit val tagsMonoid: Monoid[Tags] = new Monoid[Tags] {
    override def combine(x: Tags, y: Tags): Tags = Tags(x.tags ++ y.tags)

    override def empty: Tags = Tags.empty

  }
}
