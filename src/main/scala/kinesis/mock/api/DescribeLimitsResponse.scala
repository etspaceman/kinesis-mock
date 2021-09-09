package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe
import kinesis.mock.models._

final case class DescribeLimitsResponse(openShardCount: Int, shardLimit: Int)

object DescribeLimitsResponse {
  def get(
      shardLimit: Int,
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[DescribeLimitsResponse] =
    streamsRef.get.map { streams =>
      DescribeLimitsResponse(
        streams.streams
          .filter { case (streamArn, _) =>
            streamArn.awsRegion == awsRegion && streamArn.awsAccountId == awsAccountId
          }
          .values
          .map(_.shards.keys.count(_.isOpen))
          .sum,
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
