package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_EnableEnhancedMonitoring.html
final case class EnableEnhancedMonitoringRequest(
    shardLevelMetrics: Vector[ShardLevelMetric],
    streamName: StreamName
) {
  def enableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[EnableEnhancedMonitoringResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ => CommonValidations.findStream(streamName, streams))
        .map { stream =>
          val current = stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
          val desired =
            if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
              ShardLevelMetric.values
                .filterNot(_ == ShardLevelMetric.ALL)
                .toVector
            else (current ++ shardLevelMetrics).distinct
          (
            streams.updateStream(
              stream
                .copy(enhancedMonitoring = Vector(ShardLevelMetrics(desired)))
            ),
            EnableEnhancedMonitoringResponse(
              current,
              desired,
              streamName
            )
          )
        }
        .sequenceWithDefault(streams)
    }
}

object EnableEnhancedMonitoringRequest {
  implicit val enableEnhancedMonitoringRequestCirceEncoder
      : circe.Encoder[EnableEnhancedMonitoringRequest] =
    circe.Encoder.forProduct2("ShardLevelMetrics", "StreamName")(x =>
      (x.shardLevelMetrics, x.streamName)
    )
  implicit val enableEnhancedMonitoringRequestCirceDecoder
      : circe.Decoder[EnableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield EnableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  }
  implicit val enableEnhancedMonitoringRequestEncoder
      : Encoder[EnableEnhancedMonitoringRequest] = Encoder.derive
  implicit val enableEnhancedMonitoringRequestDecoder
      : Decoder[EnableEnhancedMonitoringRequest] = Decoder.derive
  implicit val enableEnhancedMonitoringRequestEq
      : Eq[EnableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
