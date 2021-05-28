package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DisableEnhancedMonitoring.html
final case class DisableEnhancedMonitoringRequest(
    shardLevelMetrics: List[ShardLevelMetric],
    streamName: StreamName
) {
  def disableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[DisableEnhancedMonitoringResponse]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ => CommonValidations.findStream(streamName, streams))
        .traverse { stream =>
          val current =
            stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
          val desired =
            if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
              List.empty
            else current.diff(shardLevelMetrics)

          streamsRef
            .update(x =>
              x.updateStream(
                stream
                  .copy(enhancedMonitoring = List(ShardLevelMetrics(desired)))
              )
            )
            .as(
              DisableEnhancedMonitoringResponse(
                current,
                desired,
                streamName
              )
            )
        }
    }
}

object DisableEnhancedMonitoringRequest {
  implicit val disableEnhancedMonitoringRequestCirceEncoder
      : circe.Encoder[DisableEnhancedMonitoringRequest] =
    circe.Encoder.forProduct2("ShardLevelMetrics", "StreamName")(x =>
      (x.shardLevelMetrics, x.streamName)
    )
  implicit val disableEnhancedMonitoringRequestCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield DisableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  }
  implicit val disableEnhancedMonitoringRequestEncoder
      : Encoder[DisableEnhancedMonitoringRequest] = Encoder.derive
  implicit val disableEnhancedMonitoringRequestDecoder
      : Decoder[DisableEnhancedMonitoringRequest] = Decoder.derive
  implicit val disableEnhancedMonitoringRequestEq
      : Eq[DisableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
