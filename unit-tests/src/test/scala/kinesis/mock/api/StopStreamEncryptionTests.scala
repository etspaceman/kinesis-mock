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

import kinesis.mock.instances.arbitrary.{given, _}
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StopStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should stop stream encryption")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        asActive = streams.findAndUpdateStream(streamArn)(x =>
          x.copy(streamStatus = StreamStatus.ACTIVE)
        )
        keyId = keyIdGen.one
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = StopStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          None,
          Some(streamArn)
        )
        res <- req.stopStreamEncryption(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists { s =>
            s.keyId.isEmpty &&
            s.encryptionType == EncryptionType.NONE &&
            s.streamStatus == StreamStatus.UPDATING
          },
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the KMS encryption type is not used")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          asActive = streams.findAndUpdateStream(streamArn)(x =>
            x.copy(streamStatus = StreamStatus.ACTIVE)
          )
          keyId = keyIdGen.one
          streamsRef <- Ref.of[IO, Streams](asActive)
          req = StopStreamEncryptionRequest(
            EncryptionType.NONE,
            keyId,
            None,
            Some(streamArn)
          )
          res <- req.stopStreamEncryption(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(
          res.isLeft,
          s"req: $req\nres: $res\nstreams: $asActive"
        )
    }
  )

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        keyId = keyIdGen.one
        streamsRef <- Ref.of[IO, Streams](streams)
        req = StopStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          None,
          Some(streamArn)
        )
        res <- req.stopStreamEncryption(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
