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
import enumeratum.scalacheck.*
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DescribeStreamSummaryTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should describe a stream summary")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamSummaryRequest(None, Some(streamArn))
        res <- req.describeStreamSummary(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescriptionSummary = streams.streams
          .get(streamArn)
          .map(s => StreamDescriptionSummary.fromStreamData(s))
      yield assert(
        res.isRight && res.exists { response =>
          streamDescriptionSummary.contains(response.streamDescriptionSummary)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream does not exist")(PropF.forAllF {
    (
        req: DescribeStreamSummaryRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      for
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStreamSummary(streamsRef, awsRegion, awsAccountId)
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })
