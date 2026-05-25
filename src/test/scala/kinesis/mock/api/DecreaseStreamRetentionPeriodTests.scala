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

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import org.scalacheck.effect.PropF

import kinesis.mock.Utils
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should decrease the stream retention period")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        withIncreasedRetention = streams.findAndUpdateStream(streamArn)(
          stream =>
            stream.copy(
              retentionPeriod = 48.hours,
              streamStatus = StreamStatus.ACTIVE
            )
        )
        streamsRef <- Ref.of[IO, Streams](withIncreasedRetention)
        req = DecreaseStreamRetentionPeriodRequest(24, None, Some(streamArn))
        res <- req.decreaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $withIncreasedRetention"
      )
  })

  test(
    "It should reject when the stream retention period is less than the request"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DecreaseStreamRetentionPeriodRequest(48, None, Some(streamArn))
        res <- req.decreaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streams <- streamsRef.get
      yield assert(
        res.isLeft && streams.streams.get(streamArn).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
