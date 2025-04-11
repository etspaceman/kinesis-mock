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
package models

import scala.collection.SortedMap

import java.time.Instant

import cats.Eq
import cats.syntax.all.*
import io.circe.*

final case class Streams(streams: SortedMap[StreamArn, StreamData]):
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams ++ Seq(stream.streamArn -> stream))
  def findAndUpdateStream(
      streamArn: StreamArn
  )(f: StreamData => StreamData): Streams =
    streams
      .get(streamArn)
      .map(stream =>
        copy(streams = streams ++ Seq(stream.streamArn -> f(stream)))
      )
      .getOrElse(this)

  def addStream(
      shardCount: Int,
      streamArn: StreamArn,
      streamModeDetails: Option[StreamModeDetails],
      now: Instant
  ): Streams =
    copy(streams =
      streams ++ Seq(
        streamArn -> StreamData.create(
          shardCount,
          streamArn,
          streamModeDetails,
          now
        )
      )
    )

  def deleteStream(
      streamArn: StreamArn
  ): Streams = streams
    .get(streamArn)
    .map(stream =>
      copy(streams =
        streams ++ Seq(
          streamArn -> stream.copy(
            shards = SortedMap.empty,
            streamStatus = StreamStatus.DELETING,
            tags = Tags.empty,
            enhancedMonitoring = Vector.empty,
            consumers = SortedMap.empty
          )
        )
      )
    )
    .getOrElse(this)

  def removeStream(streamArn: StreamArn): Streams =
    copy(streams = streams.filterNot { case (x, _) => streamArn == x })

object Streams:
  val empty: Streams = Streams(SortedMap.empty)
  given streamsCirceEncoder: Encoder[Streams] =
    Encoder.forProduct1("streams")(x => x.streams)
  given streamsCirceDecoder: Decoder[Streams] = x =>
    for streams <- x.downField("streams").as[SortedMap[StreamArn, StreamData]]
    yield Streams(streams)
  given streamsEq: Eq[Streams] = (x, y) => x.streams.toMap === y.streams.toMap
