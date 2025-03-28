/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DisableEnhancedMonitoring.html
final case class DisableEnhancedMonitoringRequest(
    shardLevelMetrics: Vector[ShardLevelMetric],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def disableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DisableEnhancedMonitoringResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ => CommonValidations.findStream(arn, streams))
            .map { stream =>
              val current =
                stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
              val desired =
                if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
                  Vector.empty
                else current.diff(shardLevelMetrics)

              (
                streams.updateStream(
                  stream
                    .copy(enhancedMonitoring =
                      Vector(ShardLevelMetrics(desired))
                    )
                ),
                DisableEnhancedMonitoringResponse(
                  current,
                  desired,
                  name,
                  arn
                )
              )
            }
        }
        .sequenceWithDefault(streams)
    }
}

object DisableEnhancedMonitoringRequest {
  given disableEnhancedMonitoringRequestCirceEncoder
      : circe.Encoder[DisableEnhancedMonitoringRequest] =
    circe.Encoder.forProduct3("ShardLevelMetrics", "StreamName", "StreamARN")(
      x => (x.shardLevelMetrics, x.streamName, x.streamArn)
    )
  given disableEnhancedMonitoringRequestCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield DisableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  }
  given disableEnhancedMonitoringRequestEncoder
      : Encoder[DisableEnhancedMonitoringRequest] = Encoder.derive
  given disableEnhancedMonitoringRequestDecoder
      : Decoder[DisableEnhancedMonitoringRequest] = Decoder.derive
  given disableEnhancedMonitoringRequestEq
      : Eq[DisableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
