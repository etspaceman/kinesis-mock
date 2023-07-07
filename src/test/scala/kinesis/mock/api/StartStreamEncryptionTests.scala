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

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StartStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should start stream encryption")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val asActive = streams.findAndUpdateStream(streamArn)(x =>
        x.copy(streamStatus = StreamStatus.ACTIVE)
      )

      val keyId = keyIdGen.one

      for {
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = StartStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          None,
          Some(streamArn)
        )
        res <- req.startStreamEncryption(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists { s =>
            s.keyId.contains(keyId) &&
            s.encryptionType == EncryptionType.KMS &&
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
        val streams =
          Streams.empty.addStream(1, streamArn, None)

        val asActive = streams.findAndUpdateStream(streamArn)(x =>
          x.copy(streamStatus = StreamStatus.ACTIVE)
        )

        val keyId = keyIdGen.one

        for {
          streamsRef <- Ref.of[IO, Streams](asActive)
          req = StartStreamEncryptionRequest(
            EncryptionType.NONE,
            keyId,
            None,
            Some(streamArn)
          )
          res <- req.startStreamEncryption(
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
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val keyId = keyIdGen.one

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = StartStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          None,
          Some(streamArn)
        )
        res <- req.startStreamEncryption(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res\nstreams: $streams")
  })
}
