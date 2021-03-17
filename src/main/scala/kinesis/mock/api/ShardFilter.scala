package kinesis.mock.api

import java.time.Instant

import cats.kernel.Eq
import io.circe._

final case class ShardFilter(
    shardId: Option[String],
    timestamp: Option[Instant],
    `type`: ShardFilterType
)

object ShardFilter {
  implicit val shardFilterCirceEncoder: Encoder[ShardFilter] =
    Encoder.forProduct3("ShardId", "Timestamp", "Type")(x =>
      (x.shardId, x.timestamp, x.`type`)
    )

  implicit val shardFilterCirceDecoder: Decoder[ShardFilter] = x =>
    for {
      shardId <- x.downField("ShardId").as[Option[String]]
      timestamp <- x.downField("Timestamp").as[Option[Instant]]
      `type` <- x.downField("Type").as[ShardFilterType]
    } yield ShardFilter(shardId, timestamp, `type`)

  implicit val shardFilterEq: Eq[ShardFilter] = Eq.fromUniversalEquals
}
