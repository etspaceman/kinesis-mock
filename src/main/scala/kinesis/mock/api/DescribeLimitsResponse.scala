package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class DescribeLimitsResponse(
    onDemandStreamCount: Int,
    onDemandStreamCountLimit: Int,
    openShardCount: Int,
    shardLimit: Int
)

object DescribeLimitsResponse {
  def get(
      shardLimit: Int,
      onDemandStreamCountLimit: Int,
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[DescribeLimitsResponse] =
    streamsRef.get.map { streams =>
      DescribeLimitsResponse(
        streams.streams.count { case (_, stream) =>
          stream.streamModeDetails.streamMode === StreamMode.ON_DEMAND
        },
        onDemandStreamCountLimit,
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
    circe.Encoder.forProduct4(
      "OnDemandStreamCount",
      "OnDemandStreamCountLimit",
      "OpenShardCount",
      "ShardLimit"
    )(x =>
      (
        x.onDemandStreamCount,
        x.onDemandStreamCountLimit,
        x.openShardCount,
        x.shardLimit
      )
    )
  implicit val describeLimitsResponseCirceDecoder
      : circe.Decoder[DescribeLimitsResponse] = { x =>
    for {
      onDemandStreamCount <- x.downField("OnDemandStreamCount").as[Int]
      onDemandStreamCountLimit <- x
        .downField("OnDemandStreamCountLimit")
        .as[Int]
      openShardCount <- x.downField("OpenShardCount").as[Int]
      shardLimit <- x.downField("ShardLimit").as[Int]
    } yield DescribeLimitsResponse(
      onDemandStreamCount,
      onDemandStreamCountLimit,
      openShardCount,
      shardLimit
    )
  }
  implicit val describeLimitsResponseEncoder: Encoder[DescribeLimitsResponse] =
    Encoder.derive
  implicit val describeLimitsResponseDecoder: Decoder[DescribeLimitsResponse] =
    Decoder.derive
  implicit val describeLimitsResponseEq: Eq[DescribeLimitsResponse] =
    Eq.fromUniversalEquals
}
