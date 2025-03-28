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
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models._

class CreateStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should create a stream")(PropF.forAllF {
    (
        req: CreateStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty
      val streamArn = StreamArn(awsRegion, req.streamName, awsAccountId)
      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.createStream(
          streamsRef,
          req.shardCount.getOrElse(4),
          10,
          awsRegion,
          awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.shards.size == req.shardCount.getOrElse(4)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shardCount exceeds the shardLimit")(
    PropF.forAllF {
      (
          req: CreateStreamRequest,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams = Streams.empty

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          res <- req.createStream(
            streamsRef,
            0,
            10,
            awsRegion,
            awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject if the on-demand-stream-count exceeds the limit")(
    PropF.forAllF {
      (
          req: CreateStreamRequest,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams = Streams.empty
        val reqForTest = req.copy(streamModeDetails =
          Some(StreamModeDetails(StreamMode.ON_DEMAND))
        )

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          res <- reqForTest.createStream(
            streamsRef,
            50,
            0,
            awsRegion,
            awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
}
