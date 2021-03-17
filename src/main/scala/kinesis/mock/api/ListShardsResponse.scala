package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._

final case class ListShardsResponse(
    nextToken: Option[String],
    shards: List[Shard]
)

object ListShardsResponse {
  implicit val listShardsResponseCirceEncoder: Encoder[ListShardsResponse] =
    Encoder.forProduct2("NextToken", "Shards")(x => (x.nextToken, x.shards))
  implicit val listShardsResponseCirceDecoder: Decoder[ListShardsResponse] =
    x =>
      for {
        nextToken <- x.downField("NextToken").as[Option[String]]
        shards <- x.downField("Shards").as[List[Shard]]
      } yield ListShardsResponse(nextToken, shards)
  implicit val listShardsResponseEq: Eq[ListShardsResponse] =
    Eq.fromUniversalEquals
}
