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

class UpdateMaxRecordSizeTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should update the max record size")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateMaxRecordSizeRequest(None, Some(streamArn), 2048)
        res <- req.updateMaxRecordSize(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists(_.maxRecordSizeInKiB.contains(2048)),
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when below 1024")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateMaxRecordSizeRequest(None, Some(streamArn), 500)
        res <- req.updateMaxRecordSize(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject when above 10240")(PropF.forAllF {
    (streamArn: StreamArn) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateMaxRecordSizeRequest(None, Some(streamArn), 20480)
        res <- req.updateMaxRecordSize(
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
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = UpdateMaxRecordSizeRequest(None, None, 2048)
        res <- req.updateMaxRecordSize(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
