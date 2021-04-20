package kinesis.mock.api

import cats.effect.IO
import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.Streams
import cats.effect.Ref

final case class DescribeLimitsResponse(openShardCount: Int, shardLimit: Int)

object DescribeLimitsResponse {
  def get(
      shardLimit: Int,
      streamsRef: Ref[IO, Streams]
  ): IO[DescribeLimitsResponse] =
    streamsRef.get.map { streams =>
      DescribeLimitsResponse(
        streams.streams.values.map(_.shards.keys.count(_.isOpen)).sum,
        shardLimit
      )
    }

  implicit val describeLimitsResponseCirceEncoder
      : Encoder[DescribeLimitsResponse] =
    Encoder.forProduct2("OpenShardCount", "ShardLimit")(x =>
      (x.openShardCount, x.shardLimit)
    )
  implicit val describeLimitsResponseCirceDecoder
      : Decoder[DescribeLimitsResponse] = { x =>
    for {
      openShardCount <- x.downField("OpenShardCount").as[Int]
      shardLimit <- x.downField("ShardLimit").as[Int]
    } yield DescribeLimitsResponse(openShardCount, shardLimit)
  }
  implicit val describeLimitsResponseEq: Eq[DescribeLimitsResponse] =
    Eq.fromUniversalEquals
}
