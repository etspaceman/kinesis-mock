package kinesis.mock
package models

import scala.util.{Success, Try}

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

final case class SequenceNumber(value: String) {
  def numericValue: BigInt = value match {
    case "LATEST"       => BigInt(-1)
    case "TRIM_HORIZON" => BigInt(-2)
    case "AT_TIMESTAMP" => BigInt(-3)
    case "SHARD_END"    => SequenceNumber.shardEndNumber
    case x              => BigInt(x)
  }

  def parse: ValidatedNel[KinesisMockException, Either[
    String,
    SequenceNumberParts
  ]] = {
    value match {
      case "LATEST" | "TRIM_HORIZON" | "AT_TIMESTAMP" | "SHARD_END" =>
        Valid(Left(value))
      case x => {
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
          Try(BigInt(seqIndexHex.head.toString, 16)),
          Try(BigInt(shardCreateSecsHex, 16)),
          Try(BigInt(seqTimeHex, 16)),
          Try(BigInt(shardIndexHex, 16))
        ) match {
          case (
                Success(seqIndex),
                _,
                _,
                _
              ) if seqIndex.toInt > 7 =>
            InvalidArgumentException("Sequence index too high").invalidNel
          case (
                _,
                Success(shardCreatedSecs),
                _,
                _
              ) if shardCreatedSecs.toLong > 16025175000L =>
            InvalidArgumentException(
              s"Date too large: $shardCreatedSecs"
            ).invalidNel
          case (
                Success(seqIndex),
                Success(shardCreateSecs),
                Success(seqTime),
                Success(shardIndex)
              ) =>
            Valid(
              Right(
                SequenceNumberParts(
                  Instant.ofEpochSecond(shardCreateSecs.toLong),
                  shardIndex.toInt,
                  hex.slice(27, 29),
                  seqIndex.toInt,
                  Instant.ofEpochSecond(seqTime.toLong)
                )
              )
            )
          case _ =>
            InvalidArgumentException(
              "SequenceNumber could not be parsed"
            ).invalidNel
        }

      }
    }
  }
}

final case class SequenceNumberParts(
    shardCreateTime: Instant,
    shardIndex: Int,
    byte1: String,
    seqIndex: Int,
    seqTime: Instant
)

object SequenceNumber {
  val shardEndNumber = BigInt("7fffffffffffffff", 16)
  val shardEnd = SequenceNumber(shardEndNumber.toString)
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
          ("00000000" + BigInt(shardCreateTime.getEpochSecond()).toString(16))
            .takeRight(9) +
          BigInt(shardIndex).toString(16).takeRight(1) +
          ("0000000000000000" + BigInt(seqIndex.getOrElse(0)).toString(16))
            .takeRight(16) +
          byte1.getOrElse("00") +
          "00000000" + BigInt(
            seqTime.getOrElse(shardCreateTime).getEpochSecond()
          )
            .toString(16)
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
}
