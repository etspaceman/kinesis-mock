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

package kinesis.mock.models

import scala.util.Try

import cats.Eq
import cats.syntax.all.*
import io.circe.*

final case class StreamArn(
    awsRegion: AwsRegion,
    streamName: StreamName,
    awsAccountId: AwsAccountId
):
  val streamArn: String =
    s"arn:${awsRegion.awsArnPiece}:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName"
  override def toString: String = streamArn

object StreamArn:
  def fromArn(streamArn: String): Either[String, StreamArn] =
    for
      streamName <- Try(streamArn.split("/")(1)).toEither.bimap(
        e => s"Could not get stream name from ARN: ${e.getMessage}",
        StreamName.apply
      )
      streamParts = streamArn.split(":")
      awsRegion <- Try(streamParts(3)).toEither
        .leftMap(_.getMessage)
        .flatMap(
          AwsRegion
            .withNameEither(_)
            .leftMap(e => s"Could not get awsRegion from ARN: ${e.getMessage}")
        )
      awsAccountId <- Try(streamParts(4)).toEither.bimap(
        e => s"Could not get awsAccountId from ARN: ${e.getMessage}",
        AwsAccountId.apply
      )
    yield StreamArn(awsRegion, streamName, awsAccountId)

  given streamArnCirceEncoder: Encoder[StreamArn] =
    Encoder[String].contramap(_.streamArn)
  given streamArnCirceDecoder: Decoder[StreamArn] =
    Decoder[String].emap(StreamArn.fromArn)
  given streamArnCirceKeyEncoder: KeyEncoder[StreamArn] =
    KeyEncoder[String].contramap(_.streamArn)
  given streamArnCirceKeyDecoder: KeyDecoder[StreamArn] =
    KeyDecoder.instance(StreamArn.fromArn(_).toOption)
  given streamArnEq: Eq[StreamArn] = (x, y) =>
    x.awsRegion === y.awsRegion &&
      x.streamName === y.streamName &&
      x.awsAccountId === y.awsAccountId &&
      x.streamArn === y.streamArn
  given streamArnOrdering: Ordering[StreamArn] =
    (x: StreamArn, y: StreamArn) =>
      Ordering[String].compare(x.streamArn, y.streamArn)
