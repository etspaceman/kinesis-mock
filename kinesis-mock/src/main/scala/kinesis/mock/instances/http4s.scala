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
package instances

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either.*
import fs2.Chunk
import io.bullet.borer.Cbor
import io.circe.{Json, Printer}
import org.http4s.*
import org.http4s.circe.jsonOfWithMedia
import org.http4s.headers.`Content-Type`

object http4s:
  implicit def kinesisMockEntityDecoder[A: Decoder]: EntityDecoder[IO, A] =
    EntityDecoder.decodeBy[IO, A](
      KinesisMockMediaTypes.validContentTypes.head,
      KinesisMockMediaTypes.validContentTypes.tail.toVector*
    ) { msg =>
      msg.contentType match
        case Some(ct)
            if ct.mediaType == KinesisMockMediaTypes.amazonJson ||
              (ct.mediaType.isApplication && ct.mediaType.subType.startsWith(
                "json"
              )) =>
          given D: io.circe.Decoder[A] = Decoder[A].circeDecoder
          jsonOfWithMedia[IO, A](
            KinesisMockMediaTypes.amazonJson,
            MediaType.application.json
          ).decode(msg, strict = false)

        case Some(ct) if ct.mediaType == KinesisMockMediaTypes.amazonCbor =>
          given D: io.bullet.borer.Decoder[A] = Decoder[A].borerDecoder
          EntityDecoder.collectBinary[IO](msg).flatMap { chunks =>
            EitherT.fromEither(
              Cbor.decode(chunks.toArray).to[A].valueEither.leftMap { err =>
                MalformedMessageBodyFailure(err.getMessage(), Some(err))
              }
            )
          }
        case Some(ct) =>
          EitherT.leftT(
            MediaTypeMismatch(
              ct.mediaType,
              KinesisMockMediaTypes.validContentTypes
            )
          )
        case None =>
          EitherT.leftT(
            MediaTypeMissing(KinesisMockMediaTypes.validContentTypes)
          )
    }

  def kinesisMockEntityEncoder[A: Encoder](
      mediaType: MediaType
  ): EntityEncoder[IO, A] =
    mediaType match
      case KinesisMockMediaTypes.amazonJson | MediaType.application.json =>
        EntityEncoder[IO, Chunk[Byte]]
          .contramap[Json](fromJsonToChunk(Printer.noSpaces))
          .withContentType(`Content-Type`(mediaType))
          .contramap(Encoder[A].circeEncoder.apply)
      case _ =>
        given E: io.bullet.borer.Encoder[A] = Encoder[A].borerEncoder
        EntityEncoder[IO, Chunk[Byte]]
          .contramap[A](x => Chunk.array(Cbor.encode(x).toByteArray))
          .withContentType(`Content-Type`(mediaType))

  private def fromJsonToChunk(printer: Printer)(json: Json): Chunk[Byte] =
    Chunk.ByteBuffer.view(printer.printToByteBuffer(json))
