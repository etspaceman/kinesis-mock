package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import io.circe

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
      : circe.Encoder[DescribeLimitsResponse] =
    circe.Encoder.forProduct2("OpenShardCount", "ShardLimit")(x =>
      (x.openShardCount, x.shardLimit)
    )
  implicit val describeLimitsResponseCirceDecoder
      : circe.Decoder[DescribeLimitsResponse] = { x =>
    for {
      openShardCount <- x.downField("OpenShardCount").as[Int]
      shardLimit <- x.downField("ShardLimit").as[Int]
    } yield DescribeLimitsResponse(openShardCount, shardLimit)
  }
  implicit val describeLimitsResponseEncoder: Encoder[DescribeLimitsResponse] =
    Encoder.derive
  implicit val describeLimitsResponseDecoder: Decoder[DescribeLimitsResponse] =
    Decoder.derive
  implicit val describeLimitsResponseEq: Eq[DescribeLimitsResponse] =
    Eq.fromUniversalEquals
}
