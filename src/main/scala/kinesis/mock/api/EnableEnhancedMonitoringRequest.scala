package kinesis.mock
package api

import cats.data._
import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_EnableEnhancedMonitoring.html
final case class EnableEnhancedMonitoringRequest(
    shardLevelMetrics: List[ShardLevelMetric],
    streamName: String
) {
  def enableEnhancedMonitoring(
      streams: Streams
  ): ValidatedNel[
    KinesisMockException,
    (Streams, EnableEnhancedMonitoringResponse)
  ] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        CommonValidations.validateStreamName(streamName).map { _ =>
          val current = stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
          val desired =
            if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
              ShardLevelMetric.values
                .filterNot(_ == ShardLevelMetric.ALL)
                .toList
            else (current ++ shardLevelMetrics).distinct

          (
            streams.updateStream(
              stream.copy(enhancedMonitoring = List(ShardLevelMetrics(desired)))
            ),
            EnableEnhancedMonitoringResponse(
              current,
              desired,
              streamName
            )
          )
        }
      )
}

object EnableEnhancedMonitoringRequest {
  implicit val enableEnhancedMonitoringRequestEncoder
      : Encoder[EnableEnhancedMonitoringRequest] =
    Encoder.forProduct2("ShardLevelMetrics", "StreamName")(x =>
      (x.shardLevelMetrics, x.streamName)
    )
  implicit val enableEnhancedMonitoringRequestDecoder
      : Decoder[EnableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[String]
    } yield EnableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  }
  implicit val enableEnhancedMonitoringRequestEq
      : Eq[EnableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
