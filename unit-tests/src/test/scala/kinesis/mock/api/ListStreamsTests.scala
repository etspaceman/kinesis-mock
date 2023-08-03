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
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list streams")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      for {
        now <- Utils.now
        streamNames = Gen
          .listOfN(10, Arbitrary.arbitrary[StreamName])
          .suchThat(x =>
            x.groupBy(_.streamName)
              .collect { case (_, y) if y.length > 1 => y }
              .isEmpty
          )
          .one
          .sorted
        streams = streamNames.foldLeft(Streams.empty) {
          case (streams, streamName) =>
            streams.addStream(
              1,
              StreamArn(awsRegion, streamName, awsAccountId),
              None,
              now
            )
        }
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(None, None)
        res <- req.listStreams(streamsRef, awsRegion, awsAccountId)
      } yield assert(
        res.isRight && res.exists { response =>
          streamNames == response.streamNames
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })

  test("It should filter by exclusiveStartStreamName")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      for {
        now <- Utils.now
        streamNames = Gen
          .listOfN(10, Arbitrary.arbitrary[StreamName])
          .suchThat(x =>
            x.groupBy(_.streamName)
              .collect { case (_, y) if y.length > 1 => y }
              .isEmpty
          )
          .one
          .sorted
        streams = streamNames.foldLeft(Streams.empty) {
          case (streams, streamName) =>
            streams.addStream(
              1,
              StreamArn(awsRegion, streamName, awsAccountId),
              None,
              now
            )
        }
        exclusiveStartStreamName = streamNames(3)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(Some(exclusiveStartStreamName), None)
        res <- req.listStreams(streamsRef, awsRegion, awsAccountId)
      } yield assert(
        res.isRight && res.exists { response =>
          streamNames.takeRight(6) == response.streamNames
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })

  test("It should limit properly")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      for {
        now <- Utils.now
        streamNames = Gen
          .listOfN(10, Arbitrary.arbitrary[StreamName])
          .suchThat(x =>
            x.groupBy(_.streamName)
              .collect { case (_, y) if y.length > 1 => y }
              .isEmpty
          )
          .one
          .sorted
        streams = streamNames.foldLeft(Streams.empty) {
          case (streams, streamName) =>
            streams.addStream(
              1,
              StreamArn(awsRegion, streamName, awsAccountId),
              None,
              now
            )
        }
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(None, Some(5))
        res <- req.listStreams(streamsRef, awsRegion, awsAccountId)
      } yield assert(
        res.isRight && res.exists { response =>
          streamNames.take(5) == response.streamNames &&
          response.hasMoreStreams
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })
}
