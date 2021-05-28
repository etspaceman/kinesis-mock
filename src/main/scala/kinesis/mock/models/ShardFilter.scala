package kinesis.mock
package models

import java.time.Instant

import cats.kernel.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class ShardFilter(
    shardId: Option[String],
    timestamp: Option[Instant],
    `type`: ShardFilterType
)

object ShardFilter {
  def shardFilterCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ShardFilter] =
    circe.Encoder.forProduct3("ShardId", "Timestamp", "Type")(x =>
      (x.shardId, x.timestamp, x.`type`)
    )

  def shardFilterCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ShardFilter] = x =>
    for {
      shardId <- x.downField("ShardId").as[Option[String]]
      timestamp <- x.downField("Timestamp").as[Option[Instant]]
      `type` <- x.downField("Type").as[ShardFilterType]
    } yield ShardFilter(shardId, timestamp, `type`)

  implicit val shardFilterEncoder: Encoder[ShardFilter] = Encoder.instance(
    shardFilterCirceEncoder(instantBigDecimalCirceEncoder),
    shardFilterCirceEncoder(instantLongCirceEncoder)
  )
  implicit val shardFilterDecoder: Decoder[ShardFilter] = Decoder.instance(
    shardFilterCirceDecoder(instantBigDecimalCirceDecoder),
    shardFilterCirceDecoder(instantLongCirceDecoder)
  )

  implicit val shardFilterEq: Eq[ShardFilter] = (x, y) =>
    x.shardId == y.shardId &&
      x.timestamp.map(_.getEpochSecond()) == y.timestamp.map(
        _.getEpochSecond()
      ) &&
      x.`type` == y.`type`
}
