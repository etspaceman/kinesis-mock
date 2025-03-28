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

package kinesis.mock.instances

import scala.annotation.switch

import io.bullet.borer.encodings.BaseEncoding
import io.bullet.borer.{compat, _}
import io.circe.{Json, JsonNumber}

// See https://github.com/sirthias/borer/issues/418
object borer {
  implicit def borerEncoderFromCirceEncoder[T](implicit
      ce: io.circe.Encoder[T]
  ): Encoder[T] =
    compat.circe.borerEncoderFromCirceEncoder
  private def defaultDecodeByteArray: Array[Byte] => Json =
    bytes => Json.fromString(new String(BaseEncoding.base64.encode(bytes)))
  def circeJsonAstDecoder: Decoder[Json] =
    new Decoder[Json] { self =>
      import DataItem.{Shifts => DIS}

      private[this] val arrayDecoder = Decoder.fromFactory[Json, Vector]
      private[this] val mapDecoder = Decoder.forListMap[String, Json]

      def read(r: Reader) =
        (Integer.numberOfTrailingZeros(r.dataItem()): @switch) match {
          case DIS.Null => r.readNull(); Json.Null

          case DIS.Undefined => Json.Null

          case DIS.Boolean => if (r.readBoolean()) Json.True else Json.False

          case DIS.Int  => Json.fromInt(r.readInt())
          case DIS.Long => Json.fromLong(r.readLong())

          case DIS.OverLong => Json.fromBigInt(Decoder.forBigInt.read(r))

          case DIS.Float16 | DIS.Float =>
            val float = r.readFloat()
            Json.fromFloat(float) getOrElse r.validationFailure(
              s"Float value `$float` cannot be represented in the circe JSON AST"
            )

          case DIS.Double =>
            val double = r.readDouble()
            Json.fromDouble(double) getOrElse r.validationFailure(
              s"Double value `$double` cannot be represented in the circe JSON AST"
            )

          case DIS.NumberString =>
            Json.fromJsonNumber(
              JsonNumber.fromDecimalStringUnsafe(r.readNumberString())
            )

          case DIS.Bytes | DIS.BytesStart =>
            defaultDecodeByteArray(r.readByteArray())

          case DIS.Chars | DIS.String | DIS.Text | DIS.TextStart =>
            Json.fromString(r.readString())

          case DIS.SimpleValue =>
            r.validationFailure(
              "Raw byte arrays cannot be represented in the circe JSON AST"
            )

          case DIS.ArrayHeader | DIS.ArrayStart =>
            Json.fromValues(arrayDecoder.read(r))

          case DIS.MapHeader | DIS.MapStart =>
            Json.fromFields(mapDecoder.read(r))

          case DIS.Tag =>
            if (r.hasTag(Tag.PositiveBigNum) | r.hasTag(Tag.NegativeBigNum)) {
              Json.fromBigInt(Decoder.forBigInt.read(r))
            } else if (r.hasTag(Tag.DecimalFraction)) {
              Json.fromBigDecimal(Decoder.forBigDecimal.read(r))
            } else if (r.hasTag(Tag.EpochDateTime)) {
              Json.fromLong(epochDateTimeDecoder.read(r))
            } else
              r.validationFailure(
                s"CBOR tag `${r.readTag()}` cannot be represented in the circe JSON AST`"
              )
        }
    }
  val epochDateTimeDecoder: Decoder[Long] = Decoder { r =>
    r.dataItem() match {
      case DataItem.Long                        => r.readLong()
      case _ if r.tryReadTag(Tag.EpochDateTime) => r.readLong()
      case _ => r.unexpectedDataItem(expected = "Long")
    }
  }
  implicit def defaultBorerDecoderFromCirceDecoder[T](implicit
      cd: io.circe.Decoder[T]
  ): Decoder[T] =
    compat.circe.borerDecoderFromCirceDecoder(circeJsonAstDecoder)
}
