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

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import org.scalacheck.effect.PropF

import kinesis.mock.Utils
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should delete a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        asActive = streams.findAndUpdateStream(streamArn)(x =>
          x.copy(streamStatus = StreamStatus.ACTIVE)
        )
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req.deleteStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists(_.streamStatus == StreamStatus.DELETING),
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the stream doesn't exist")(PropF.forAllF {
    (streamArn: StreamArn) =>
      val streams = Streams.empty

      for
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req
          .deleteStream(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req
          .deleteStream(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is not set"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        withConsumers = streams.findAndUpdateStream(consumerArn.streamArn)(x =>
          x.copy(
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(x.streamArn, consumerArn.consumerName, now)
            ),
            streamStatus = StreamStatus.ACTIVE
          )
        )
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = DeleteStreamRequest(None, Some(consumerArn.streamArn), None)
        res <- req.deleteStream(
          streamsRef,
          consumerArn.streamArn.awsRegion,
          consumerArn.streamArn.awsAccountId
        )
      yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is false"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(1, consumerArn.streamArn, None, now)
        withConsumers = streams.findAndUpdateStream(consumerArn.streamArn)(x =>
          x.copy(
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(x.streamArn, consumerArn.consumerName, now)
            ),
            streamStatus = StreamStatus.ACTIVE
          )
        )
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = DeleteStreamRequest(
          None,
          Some(consumerArn.streamArn),
          Some(false)
        )
        res <- req.deleteStream(
          streamsRef,
          consumerArn.streamArn.awsRegion,
          consumerArn.streamArn.awsAccountId
        )
      yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
