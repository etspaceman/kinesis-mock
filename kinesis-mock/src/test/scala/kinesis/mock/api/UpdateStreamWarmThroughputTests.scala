/*
 * Copyright 2021-2026 io.github.etspaceman
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

class UpdateStreamWarmThroughputTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  private val onDemand = Some(StreamModeDetails(StreamMode.ON_DEMAND))
  private val provisioned = Some(StreamModeDetails(StreamMode.PROVISIONED))

  test("It should update warm throughput on an on-demand stream")(
    PropF.forAllF { (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, onDemand, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateStreamWarmThroughputRequest(None, Some(streamArn), 10)
        res <- req.updateStreamWarmThroughput(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists(_.warmThroughputMiBps.contains(10)),
        s"req: $req\nres: $res"
      )
    }
  )

  test("It should reject on a provisioned stream")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, provisioned, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateStreamWarmThroughputRequest(None, Some(streamArn), 10)
        res <- req.updateStreamWarmThroughput(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject a negative warmThroughputMiBps")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, onDemand, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateStreamWarmThroughputRequest(None, Some(streamArn), -1)
        res <- req.updateStreamWarmThroughput(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject when neither StreamName nor StreamARN provided")(
    PropF.forAllF { (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, onDemand, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateStreamWarmThroughputRequest(None, None, 10)
        res <- req.updateStreamWarmThroughput(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
