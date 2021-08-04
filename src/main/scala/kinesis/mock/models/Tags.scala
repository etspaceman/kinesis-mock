package kinesis.mock.models

import scala.collection.SortedMap

import cats.{Eq, Monoid}
import io.circe._

final case class Tags(tags: SortedMap[String, String]) {
  def size: Int = tags.size
  def --(keys: IterableOnce[String]): Tags = copy(tags = tags.filterNot {
    case (key, _) => keys.iterator.contains(key)
  })
  override def toString: String = tags.toString()
  def toVector: Vector[(String, String)] = tags.toVector
}

object Tags {
  def empty: Tags = Tags(SortedMap.empty)
  def fromTagList(tagList: TagList): Tags = Tags(
    SortedMap.from(tagList.tags.map(x => (x.key, x.value)))
  )
  implicit val tagsCirceEncoder: Encoder[Tags] =
    Encoder[SortedMap[String, String]].contramap(_.tags)
  implicit val tagsCirceDecoder: Decoder[Tags] =
    Decoder[SortedMap[String, String]].map(Tags.apply)
  implicit val tagsEq: Eq[Tags] = Eq.fromUniversalEquals
  implicit val tagsMonoid: Monoid[Tags] = new Monoid[Tags] {
    override def combine(x: Tags, y: Tags): Tags = Tags(x.tags ++ y.tags)

    override def empty: Tags = Tags.empty

  }
}
