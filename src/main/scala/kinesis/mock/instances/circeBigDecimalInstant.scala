package kinesis.mock.instances

import java.time.Instant

import io.circe._

object circeBigDecimalInstant {
  private def encodeInstant(i: Instant): java.math.BigDecimal =
    java.math.BigDecimal.valueOf(i.toEpochMilli()).scaleByPowerOfTen(-3)

  private def decodeInstant(bd: java.math.BigDecimal): Instant =
    Instant.ofEpochMilli(bd.scaleByPowerOfTen(3).longValue())

  implicit val instantCirceEncoder: Encoder[Instant] =
    Encoder[java.math.BigDecimal].contramap(encodeInstant)

  implicit val instantCirceDecoder: Decoder[Instant] =
    Decoder[java.math.BigDecimal].map(decodeInstant)
}
