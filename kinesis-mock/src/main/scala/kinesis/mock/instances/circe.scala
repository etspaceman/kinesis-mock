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

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

import io.circe.syntax.*
import io.circe.{Decoder, Encoder, JsonObject}

object circe:
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

  given Encoder[TimeUnit] =
    Encoder[String].contramap(_.name())
  given Decoder[TimeUnit] =
    Decoder[String].emapTry(x => Try(TimeUnit.valueOf(x)))

  given Encoder[FiniteDuration] =
    x => JsonObject("length" -> x.length.asJson, "unit" -> x.unit.asJson).asJson

  given Decoder[FiniteDuration] = x =>
    for
      length <- x.downField("length").as[Long]
      unit <- x.downField("unit").as[TimeUnit]
    yield FiniteDuration(length, unit)

  given Encoder[Array[Byte]] =
    Encoder[String].contramap(str =>
      new String(Base64.getEncoder.encode(str), "UTF-8")
    )

  given Decoder[Array[Byte]] =
    Decoder[String].map(str => Base64.getDecoder.decode(str))
