package kinesis.mock.api

import cats.effect.{Concurrent, IO}
import io.circe._

import kinesis.mock.models._

final case class ListShardsResponse(
    nextToken: Option[String],
    shards: List[Shard]
)

object ListShardsResponse {
  implicit val listShardsResponseCirceEncoder: Encoder[ListShardsResponse] =
    Encoder.forProduct2("NextToken", "Shards")(x => (x.nextToken, x.shards))
  implicit def listShardsResponseCirceDecoder(implicit
      C: Concurrent[IO]
  ): Decoder[ListShardsResponse] =
    x =>
      for {
        nextToken <- x.downField("NextToken").as[Option[String]]
        shards <- x.downField("Shards").as[List[Shard]]
      } yield ListShardsResponse(nextToken, shards)
}
