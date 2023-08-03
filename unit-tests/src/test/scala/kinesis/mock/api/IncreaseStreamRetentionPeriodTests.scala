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

package kinesis.mock.api

import scala.concurrent.duration._

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.Utils
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class IncreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should increase the stream retention period")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(
          _.copy(streamStatus = StreamStatus.ACTIVE)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        req = IncreaseStreamRetentionPeriodRequest(48, None, Some(streamArn))
        res <- req.increaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight &&
          s.streams.get(streamArn).exists { stream =>
            stream.retentionPeriod == 48.hours
          },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream retention period is greater than the request"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        withUpdatedRetention = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(retentionPeriod = 72.hours, streamStatus = StreamStatus.ACTIVE)
        )
        streamsRef <- Ref.of[IO, Streams](withUpdatedRetention)
        req = IncreaseStreamRetentionPeriodRequest(48, None, Some(streamArn))
        res <- req.increaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res\nstreams: $streams")
  })
}
