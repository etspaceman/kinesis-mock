package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models._

final case class UpdateShardCountResponse(
    currentShardCount: Int,
    streamName: StreamName,
    targetShardCount: Int
)

object UpdateShardCountResponse {
  implicit val updateShardCountResponseCirceEncoder
      : circe.Encoder[UpdateShardCountResponse] =
    circe.Encoder.forProduct3(
      "CurrentShardCount",
      "StreamName",
      "TargetShardCount"
    )(x => (x.currentShardCount, x.streamName, x.targetShardCount))
  implicit val updateShardCountResponseCirceDecoder
      : circe.Decoder[UpdateShardCountResponse] = x =>
    for {
      currentShardCount <- x.downField("CurrentShardCount").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    } yield UpdateShardCountResponse(
      currentShardCount,
      streamName,
      targetShardCount
    )
  implicit val updateShardCountResponseEncoder
      : Encoder[UpdateShardCountResponse] = Encoder.derive
  implicit val updateShardCountResponseDecoder
      : Decoder[UpdateShardCountResponse] = Decoder.derive
  implicit val updateShardCountResponseEq: Eq[UpdateShardCountResponse] =
    Eq.fromUniversalEquals
}
