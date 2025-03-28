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
package models

import scala.util.{Success, Try}

import java.time.Instant

import cats.Eq
import cats.syntax.all._
import io.circe._

final case class SequenceNumber(value: String) {
  def numericValue: BigInt =
    SequenceNumberConstant.withNameOption(value) match {
      case None                                      => BigInt(value)
      case Some(SequenceNumberConstant.LATEST)       => BigInt(-1)
      case Some(SequenceNumberConstant.TRIM_HORIZON) => BigInt(-2)
      case Some(SequenceNumberConstant.AT_TIMESTAMP) => BigInt(-3)
      case Some(SequenceNumberConstant.SHARD_END) =>
        SequenceNumber.shardEndNumber
    }

  def parse: Response[SequenceNumberParseResult] =
    value match {
      case x if SequenceNumberConstant.withNameOption(x).nonEmpty =>
        Right(SequenceNumberConstantResult(SequenceNumberConstant.withName(x)))
      case x =>
        val seqNum =
          if (BigInt(x) < BigInt(2).pow(124))
            BigInt(x) + BigInt(2).pow(124)
          else BigInt(x)

        val hex = seqNum.toString(16)
        val seqIndexHex = hex.slice(11, 27)
        val shardCreateSecsHex = hex.slice(1, 10)
        val seqTimeHex = hex.slice(29, 38)
        val shardIndexHex = {
          val initial = hex.slice(38, 46)
          if (Try(BigInt(initial.head.toString, 16)).exists(_ > 7))
            s"-$initial"
          else initial
        }
        (
          Try(BigInt(seqIndexHex, 16)),
          Try(BigInt(shardCreateSecsHex, 16)),
          Try(BigInt(seqTimeHex, 16)),
          Try(BigInt(shardIndexHex, 16))
        ) match {
          case (
                Success(_),
                _,
                _,
                _
              )
              if seqIndexHex.headOption
                .map(x => BigInt(x.toString, 16))
                .exists(_ > 7) =>
            InvalidArgumentException("Sequence index too high").asLeft
          case (
                _,
                Success(shardCreatedSecs),
                _,
                _
              ) if shardCreatedSecs.toLong >= 16025175000L =>
            InvalidArgumentException(
              s"Date too large: $shardCreatedSecs"
            ).asLeft
          case (
                Success(seqIndex),
                Success(shardCreateSecs),
                Success(seqTime),
                Success(shardIndex)
              ) =>
            Right(
              SequenceNumberParts(
                Instant.ofEpochSecond(shardCreateSecs.toLong),
                shardIndex.toInt,
                hex.slice(27, 29),
                seqIndex.toInt,
                Instant.ofEpochSecond(seqTime.toLong)
              )
            )
          case _ =>
            InvalidArgumentException(
              "SequenceNumber could not be parsed"
            ).asLeft
        }

    }
}

sealed trait SequenceNumberParseResult

final case class SequenceNumberParts(
    shardCreateTime: Instant,
    shardIndex: Int,
    byte1: String,
    seqIndex: Int,
    seqTime: Instant
) extends SequenceNumberParseResult

final case class SequenceNumberConstantResult(constant: SequenceNumberConstant)
    extends SequenceNumberParseResult

object SequenceNumber {
  val shardEndNumber: BigInt = BigInt("7fffffffffffffff", 16)
  val shardEnd: SequenceNumber = SequenceNumber(shardEndNumber.toString)
  // See https://github.com/mhart/kinesalite/blob/master/db/index.js#L177-L186
  def create(
      shardCreateTime: Instant,
      shardIndex: Int,
      byte1: Option[String],
      seqIndex: Option[Int],
      seqTime: Option[Instant]
  ): SequenceNumber =
    SequenceNumber(
      BigInt(
        "2" +
          ("00000000" + BigInt(shardCreateTime.getEpochSecond).toString(16))
            .takeRight(9) +
          BigInt(shardIndex).toString(16).takeRight(1) +
          ("0000000000000000" + BigInt(seqIndex.getOrElse(0)).toString(16))
            .takeRight(16) +
          byte1.getOrElse("00") +
          ("00000000" + BigInt(
            seqTime.getOrElse(shardCreateTime).getEpochSecond
          )
            .toString(16))
            .takeRight(9) +
          ("0000000" + BigInt(shardIndex).toString(16)).takeRight(8) +
          "2",
        16
      ).toString()
    )

  implicit val sequenceNumberCirceEncoder: Encoder[SequenceNumber] =
    Encoder[String].contramap(_.value)
  implicit val sequenceNumberCirceDecoder: Decoder[SequenceNumber] =
    Decoder[String].map(SequenceNumber.apply)
  implicit val sequenceNumberEq: Eq[SequenceNumber] = Eq.fromUniversalEquals
}
