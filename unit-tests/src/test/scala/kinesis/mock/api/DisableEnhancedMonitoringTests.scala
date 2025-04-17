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

import cats.effect.{IO, Ref}
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DisableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should disable enhanced monitoring")(PropF.forAllF {
    (
        streamArn: StreamArn,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        updated = streams.findAndUpdateStream(streamArn)(stream =>
          stream.copy(enhancedMonitoring =
            Vector(
              ShardLevelMetrics(
                Vector(
                  ShardLevelMetric.IncomingBytes,
                  ShardLevelMetric.IncomingRecords,
                  ShardLevelMetric.OutgoingBytes,
                  ShardLevelMetric.OutgoingRecords,
                  ShardLevelMetric.WriteProvisionedThroughputExceeded,
                  ShardLevelMetric.ReadProvisionedThroughputExceeded,
                  ShardLevelMetric.IteratorAgeMilliseconds
                )
              )
            )
          )
        )
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DisableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          None,
          Some(streamArn)
        )
        res <- req.disableEnhancedMonitoring(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
        updatedMetrics = s.streams
          .get(streamArn)
          .map(_.enhancedMonitoring.flatMap(_.shardLevelMetrics))
      yield assert(
        res.isRight && res.exists { case response =>
          updatedMetrics.contains(response.desiredShardLevelMetrics)
        },
        s"req: $req\nres: $res\nupdatedMetrics: $updatedMetrics"
      )
  })
