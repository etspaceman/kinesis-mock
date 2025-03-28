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

import cats.effect.*
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*
import kinesis.mock.validations.CommonValidations

class PutRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should put records")(PropF.forAllF {
    (
        streamArn: StreamArn,
        initReq: PutRecordsRequest
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )
        req = initReq.copy(
          streamArn = Some(streamArn),
          streamName = None
        )
        predictedShards = req.records.map(entry =>
          CommonValidations
            .computeShard(
              entry.partitionKey,
              entry.explicitHashKey,
              active.streams(streamArn)
            )
            .toOption
            .map(_._1.shardId.shardId)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.putRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.shards.values.toVector.flatten.count { rec =>
            req.records.map(_.data).exists(_.sameElements(rec.data))
          } == initReq.records.length && res.exists { r =>
            r.records.map(_.shardId) == predictedShards
          }
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when the stream is not active or updating")(
    PropF.forAllF {
      (
          streamArn: StreamArn,
          initReq: PutRecordsRequest
      ) =>
        for
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          req = initReq.copy(
            streamArn = Some(streamArn),
            streamName = None
          )
          streamsRef <- Ref.of[IO, Streams](streams)
          res <- req.putRecords(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject when the shard is closed")(
    PropF.forAllF {
      (
          streamArn: StreamArn,
          initReq: PutRecordsRequest
      ) =>
        for
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          updated = streams.findAndUpdateStream(streamArn)(s =>
            s.copy(shards = s.shards.map { case (shard, recs) =>
              shard.copy(sequenceNumberRange =
                shard.sequenceNumberRange.copy(endingSequenceNumber =
                  Some(SequenceNumber.shardEnd)
                )
              ) -> recs
            })
          )
          req = initReq.copy(
            streamArn = Some(streamArn),
            streamName = None
          )
          streamsRef <- Ref.of[IO, Streams](updated)
          res <- req
            .putRecords(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
        yield assert(
          res.isLeft,
          s"req: $req\nres: $res"
        )
    }
  )
