package kinesis.mock
package api

import cats.data._
import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DisableEnhancedMonitoring.html
final case class DisableEnhancedMonitoringRequest(
    shardLevelMetrics: List[ShardLevelMetric],
    streamName: StreamName
) {
  def disableEnhancedMonitoring(
      streams: Streams
  ): ValidatedNel[
    KinesisMockException,
    (Streams, DisableEnhancedMonitoringResponse)
  ] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        CommonValidations.validateStreamName(streamName).map { _ =>
          val current = stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
          val desired =
            if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
              List.empty
            else current.diff(shardLevelMetrics)

          (
            streams.updateStream(
              stream.copy(enhancedMonitoring = List(ShardLevelMetrics(desired)))
            ),
            DisableEnhancedMonitoringResponse(
              current,
              desired,
              streamName
            )
          )
        }
      )
}

object DisableEnhancedMonitoringRequest {
  implicit val disableEnhancedMonitoringRequestEncoder
      : Encoder[DisableEnhancedMonitoringRequest] =
    Encoder.forProduct2("ShardLevelMetrics", "StreamName")(x =>
      (x.shardLevelMetrics, x.streamName)
    )
  implicit val disableEnhancedMonitoringRequestDecoder
      : Decoder[DisableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield DisableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  }
  implicit val disableEnhancedMonitoringRequestEq
      : Eq[DisableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
