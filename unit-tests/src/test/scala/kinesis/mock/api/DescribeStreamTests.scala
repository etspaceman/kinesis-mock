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

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, None, None, Some(streamArn))
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
          .map(s => StreamDescription.fromStreamData(s, None, None))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(response.streamDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should limit the shard count")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(2, streamArn, None, now)
        limit = Some(1)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, limit, None, Some(streamArn))
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
          .map(s => StreamDescription.fromStreamData(s, None, limit))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(
            response.streamDescription
          ) && response.streamDescription.shards.size == 1
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should start after a shardId")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(4, streamArn, None, now)
        exclusiveStartShardId = streams.streams
          .get(streamArn)
          .flatMap(_.shards.headOption.map(_._1.shardId.shardId))
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(
          exclusiveStartShardId,
          None,
          None,
          Some(streamArn)
        )
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
          .map(s =>
            StreamDescription.fromStreamData(s, exclusiveStartShardId, None)
          )
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(response.streamDescription) &&
          response.streamDescription.shards.size == 3 &&
          !response.streamDescription.shards.exists(x =>
            exclusiveStartShardId.contains(x.shardId)
          )
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream does not exist")(PropF.forAllF {
    (
        req: DescribeStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStream(streamsRef, awsRegion, awsAccountId)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
