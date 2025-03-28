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

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class ListStreamsRequest(
    exclusiveStartStreamName: Option[StreamName],
    limit: Option[Int]
) {
  def listStreams(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[ListStreamsResponse]] = streamsRef.get.map(streams =>
    (
      exclusiveStartStreamName match {
        case Some(streamName) =>
          CommonValidations.validateStreamName(streamName)
        case None => Right(())
      },
      limit match {
        case Some(l) => CommonValidations.validateLimit(l)
        case None    => Right(())
      }
    ).mapN { (_, _) =>
      val allStreams = streams.streams.keys.toVector
        .filter(x => x.awsRegion == awsRegion && x.awsAccountId == awsAccountId)
        .map(_.streamName)
      val lastStreamIndex = allStreams.length - 1
      val lim = limit.map(l => Math.min(l, 100)).getOrElse(100)
      val firstIndex = exclusiveStartStreamName
        .map(x => allStreams.indexOf(x) + 1)
        .getOrElse(0)
      val lastIndex = Math.min(firstIndex + lim, lastStreamIndex + 1)
      val streamNames = allStreams.slice(firstIndex, lastIndex)
      val hasMoreStreams =
        if (lastStreamIndex + 1 == lastIndex) false
        else true
      ListStreamsResponse(hasMoreStreams, streamNames)
    }
  )
}

object ListStreamsRequest {
  given listStreamsRequestCirceEncoder: circe.Encoder[ListStreamsRequest] =
    circe.Encoder.forProduct2("ExclusiveStartStreamName", "Limit")(x =>
      (x.exclusiveStartStreamName, x.limit)
    )

  given listStreamsRequestCirceDecoder: circe.Decoder[ListStreamsRequest] =
    x =>
      for {
        exclusiveStartStreamName <- x
          .downField("ExclusiveStartStreamName")
          .as[Option[StreamName]]
        limit <- x.downField("Limit").as[Option[Int]]
      } yield ListStreamsRequest(exclusiveStartStreamName, limit)

  given listStreamsRequestEncoder: Encoder[ListStreamsRequest] =
    Encoder.derive
  given listStreamsRequestDecoder: Decoder[ListStreamsRequest] =
    Decoder.derive
  given listStreamsRequestEq: Eq[ListStreamsRequest] =
    Eq.fromUniversalEquals
}
