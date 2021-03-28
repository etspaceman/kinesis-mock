package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.Streams

final case class DescribeLimitsResponse(openShardCount: Int, shardLimit: Int)

object DescribeLimitsResponse {
  def get(shardLimit: Int, streams: Streams): DescribeLimitsResponse =
    DescribeLimitsResponse(
      shardLimit,
      streams.streams.values.map(_.shards.keys.count(_.isOpen)).sum
    )

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
