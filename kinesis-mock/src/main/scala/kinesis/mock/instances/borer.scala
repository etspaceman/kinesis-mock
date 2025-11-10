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

import scala.annotation.{switch, tailrec}
import scala.collection.Factory

import io.bullet.borer.encodings.BaseEncoding
import io.bullet.borer.{compat, *}
import io.circe.{Json, JsonNumber}

// See https://github.com/sirthias/borer/issues/418
object borer:
  implicit def borerEncoderFromCirceEncoder[T](using
      ce: io.circe.Encoder[T]
  ): Encoder[T] =
    compat.circe.borerEncoderFromCirceEncoder

  private def defaultDecodeByteArray: Array[Byte] => Json =
    bytes => Json.fromString(new String(BaseEncoding.base64.encode(bytes)))

  def circeJsonAstDecoder(
      bigIntDecoder: Decoder[BigInt] = Decoder.forBigInt,
      bigDecimalDecoder: Decoder[BigDecimal] = Decoder.forBigDecimal,
      decodeUndefined: Option[() => Json] = Some(() => Json.Null),
      decodeByteArray: Option[Array[Byte] => Json] = Some(
        defaultDecodeByteArray
      ),
      decodeSimpleValue: Option[SimpleValue => Json] = None
  ): Decoder[Json] =

    new Decoder[Json]:
      import DataItem.Shifts as DIS

      private[this] val arrayDecoder =
        Decoder.fromFactory(this, implicitly[Factory[Json, Vector[Json]]])
      private[this] val mapDecoder =
        Decoder { r =>
          val buf =
            new scala.collection.mutable.ArrayBuilder.ofRef[(String, Json)]
          if r.hasMapHeader then
            @tailrec def rec(remaining: Int): Array[(String, Json)] =
              if remaining > 0 then
                buf.addOne(r.readString() -> r.read[Json]())
                rec(remaining - 1)
              else buf.result()
            val size = r.readMapHeader()
            if size <= Int.MaxValue then rec(size.toInt)
            else
              r.overflow(
                s"Cannot deserialize Map with size $size (> Int.MaxValue)"
              )
          else if r.hasMapStart then
            r.readMapStart()
            while !r.tryReadBreak() do
              buf.addOne(r.readString() -> r.read[Json]())
            buf.result()
          else r.unexpectedDataItem(expected = "Map")
        }

      def read(r: Reader): Json =
        (Integer.numberOfTrailingZeros(r.dataItem()): @switch) match
          case DIS.Null => r.readNull(); Json.Null

          case DIS.Undefined =>
            decodeUndefined match
              case Some(f) => f()
              case None    =>
                r.validationFailure(
                  "CBOR `undefined` cannot be represented in the circe JSON AST"
                )

          case DIS.Boolean => if r.readBoolean() then Json.True else Json.False

          case DIS.Int  => Json.fromInt(r.readInt())
          case DIS.Long => Json.fromLong(r.readLong())

          case DIS.OverLong => Json.fromBigInt(bigIntDecoder.read(r))

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
            decodeByteArray match
              case Some(f) => f(r.readByteArray())
              case None    =>
                r.validationFailure(
                  "Raw byte arrays cannot be represented in the circe JSON AST"
                )

          case DIS.Chars | DIS.String | DIS.Text | DIS.TextStart =>
            Json.fromString(r.readString())

          case DIS.SimpleValue =>
            decodeSimpleValue match
              case Some(f) => f(SimpleValue(r.readSimpleValue()))
              case None    =>
                r.validationFailure(
                  "Raw byte arrays cannot be represented in the circe JSON AST"
                )

          case DIS.ArrayHeader | DIS.ArrayStart =>
            Json.fromValues(arrayDecoder.read(r))

          case DIS.MapHeader | DIS.MapStart =>
            Json.fromFields(mapDecoder.read(r))

          case DIS.Tag =>
            if r.hasTag(Tag.PositiveBigNum) | r.hasTag(Tag.NegativeBigNum) then
              Json.fromBigInt(bigIntDecoder.read(r))
            else if r.hasTag(Tag.DecimalFraction) then
              Json.fromBigDecimal(bigDecimalDecoder.read(r))
            else if r.hasTag(Tag.EpochDateTime) then
              Json.fromLong(epochDateTimeDecoder.read(r))
            else
              r.validationFailure(
                s"CBOR tag `${r.readTag()}` cannot be represented in the circe JSON AST`"
              )

  val epochDateTimeDecoder: Decoder[Long] = Decoder { r =>
    r.dataItem() match
      case DataItem.Long                        => r.readLong()
      case _ if r.tryReadTag(Tag.EpochDateTime) => r.readLong()
      case _ => r.unexpectedDataItem(expected = "Long")
  }

  implicit def defaultBorerDecoderFromCirceDecoder[T](using
      cd: io.circe.Decoder[T]
  ): Decoder[T] =
    compat.circe.borerDecoderFromCirceDecoder(circeJsonAstDecoder())
