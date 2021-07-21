package kinesis.mock
package instances

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import fs2.Chunk
import io.bullet.borer.Cbor
import io.circe.{Json, Printer}
import org.http4s._
import org.http4s.circe.jsonOfWithMedia
import org.http4s.headers.`Content-Type`

object http4s {
  implicit def kinesisMockEntityDecoder[A: Decoder]: EntityDecoder[IO, A] =
    EntityDecoder.decodeBy[IO, A](
      KinesisMockMediaTypes.validContentTypes.head,
      KinesisMockMediaTypes.validContentTypes.tail.toVector: _*
    ) { msg =>
      msg.contentType match {
        case Some(ct)
            if ct.mediaType == KinesisMockMediaTypes.amazonJson ||
              (ct.mediaType.isApplication && ct.mediaType.subType.startsWith(
                "json"
              )) => {
          implicit val D = Decoder[A].circeDecoder
          jsonOfWithMedia[IO, A](
            KinesisMockMediaTypes.amazonJson,
            MediaType.application.json
          ).decode(msg, false)
        }

        case Some(ct) if ct.mediaType == KinesisMockMediaTypes.amazonCbor => {
          implicit val D = Decoder[A].borerDecoder
          EntityDecoder.collectBinary[IO](msg).flatMap { chunks =>
            EitherT.fromEither(
              Cbor.decode(chunks.toArray).to[A].valueEither.leftMap { err =>
                MalformedMessageBodyFailure(err.getMessage(), Some(err))
              }
            )
          }
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
    }

  def kinesisMockEntityEncoder[A: Encoder](
      mediaType: MediaType
  ): EntityEncoder[IO, A] =
    mediaType match {
      case KinesisMockMediaTypes.amazonJson | MediaType.application.json => {
        EntityEncoder[IO, Chunk[Byte]]
          .contramap[Json](fromJsonToChunk(Printer.noSpaces))
          .withContentType(`Content-Type`(mediaType))
          .contramap(Encoder[A].circeEncoder.apply)
      }
      case _ => {
        implicit val E = Encoder[A].borerEncoder
        EntityEncoder[IO, Chunk[Byte]]
          .contramap[A](x => Chunk.Bytes.apply(Cbor.encode(x).toByteArray))
          .withContentType(`Content-Type`(mediaType))
      }
    }

  private def fromJsonToChunk(printer: Printer)(json: Json): Chunk[Byte] =
    Chunk.ByteBuffer.view(printer.printToByteBuffer(json))
}
