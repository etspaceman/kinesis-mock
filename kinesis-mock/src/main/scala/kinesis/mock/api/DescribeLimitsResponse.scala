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
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*

final case class DescribeLimitsResponse(
    onDemandStreamCount: Int,
    onDemandStreamCountLimit: Int,
    openShardCount: Int,
    shardLimit: Int
)

object DescribeLimitsResponse:
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

  given describeLimitsResponseCirceEncoder
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
  given describeLimitsResponseCirceDecoder
      : circe.Decoder[DescribeLimitsResponse] = x =>
    for
      onDemandStreamCount <- x.downField("OnDemandStreamCount").as[Int]
      onDemandStreamCountLimit <- x
        .downField("OnDemandStreamCountLimit")
        .as[Int]
      openShardCount <- x.downField("OpenShardCount").as[Int]
      shardLimit <- x.downField("ShardLimit").as[Int]
    yield DescribeLimitsResponse(
      onDemandStreamCount,
      onDemandStreamCountLimit,
      openShardCount,
      shardLimit
    )
  given describeLimitsResponseEncoder: Encoder[DescribeLimitsResponse] =
    Encoder.derive
  given describeLimitsResponseDecoder: Decoder[DescribeLimitsResponse] =
    Decoder.derive
  given Eq[DescribeLimitsResponse] =
    Eq.fromUniversalEquals
