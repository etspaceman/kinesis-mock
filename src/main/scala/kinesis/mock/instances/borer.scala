package kinesis.mock.instances

import scala.annotation.switch
import scala.collection.compat.Factory

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
  val circeJsonAstDecoder: Decoder[Json] = {

    new Decoder[Json] {
      import DataItem.{Shifts => DIS}

      private[this] val arrayDecoder =
        Decoder.fromFactory(this, implicitly[Factory[Json, Vector[Json]]])
      private[this] val mapDecoder = Decoder.forListMap(Decoder.forString, this)

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
