package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_EnableEnhancedMonitoring.html
final case class EnableEnhancedMonitoringRequest(
    shardLevelMetrics: List[ShardLevelMetric],
    streamName: StreamName
) {
  def enableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[EnableEnhancedMonitoringResponse]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ => CommonValidations.findStream(streamName, streams))
        .traverse { stream =>
          val current = stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
          val desired =
            if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
              ShardLevelMetric.values
                .filterNot(_ == ShardLevelMetric.ALL)
                .toList
            else (current ++ shardLevelMetrics).distinct

          streamsRef
            .update(x =>
              x.updateStream(
                stream
                  .copy(enhancedMonitoring = List(ShardLevelMetrics(desired)))
              )
            )
            .as(
              EnableEnhancedMonitoringResponse(
                current,
                desired,
                streamName
              )
            )
        }
    }
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
      streamName <- x.downField("StreamName").as[StreamName]
    } yield EnableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  }
  implicit val enableEnhancedMonitoringRequestEq
      : Eq[EnableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
