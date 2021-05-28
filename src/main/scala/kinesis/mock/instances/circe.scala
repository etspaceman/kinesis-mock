package kinesis.mock.instances

import scala.concurrent.duration.FiniteDuration

import java.time.Instant
import java.util.Base64

import io.circe.{Decoder, Encoder}

object circe {
  // Used for CBOR
  val instantLongCirceEncoder: Encoder[Instant] =
    Encoder[Long].contramap(_.toEpochMilli)
  val instantLongCirceDecoder: Decoder[Instant] =
    Decoder[Long].map(Instant.ofEpochMilli)

  // Used for (most) JSON
  val instantDoubleCirceEncoder: Encoder[Instant] =
    Encoder[Double].contramap(x =>
      java.math.BigDecimal
        .valueOf(x.toEpochMilli)
        .scaleByPowerOfTen(-3)
        .doubleValue()
    )

  val instantDoubleCirceDecoder: Decoder[Instant] =
    Decoder[Double].map(x =>
      Instant.ofEpochMilli(
        java.math.BigDecimal.valueOf(x).scaleByPowerOfTen(3).longValue()
      )
    )

  // Used for some JSON, e.g. ListShards (ShardFilter) and GetShardIteratorRequest
  val instantBigDecimalCirceEncoder: Encoder[Instant] =
    Encoder[java.math.BigDecimal].contramap(x =>
      java.math.BigDecimal
        .valueOf(x.toEpochMilli)
        .scaleByPowerOfTen(-3)
    )

  val instantBigDecimalCirceDecoder: Decoder[Instant] =
    Decoder[java.math.BigDecimal].map(x =>
      Instant.ofEpochMilli(
        x.scaleByPowerOfTen(3).longValue()
      )
    )

  implicit val finiteDurationCirceEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  implicit val arrayBytesCirceEncoder: Encoder[Array[Byte]] =
    Encoder[String].contramap(str =>
      new String(Base64.getEncoder.encode(str), "UTF-8")
    )

  implicit val arrayBytesCirceDecoder: Decoder[Array[Byte]] =
    Decoder[String].map(str => Base64.getDecoder.decode(str))
}
