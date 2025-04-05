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

import scala.util.Try

import java.time.Instant
import java.util.Base64

import cats.Eq
import cats.syntax.all.*
import io.circe.*

import kinesis.mock.validations.CommonValidations

final case class ShardIterator(value: String):
  def parse(now: Instant): Response[ShardIteratorParts] =
    val decoded = Base64.getDecoder.decode(value.replaceAll("^\"|\"$", ""))

    val decrypted = new String(
      AES.decrypt(
        decoded.drop(8),
        ShardIterator.iteratorPwdKey,
        ShardIterator.iteratorPwdIv
      ),
      "UTF-8"
    )
    val split = decrypted.split("/")
    if split.length != 5 then
      InvalidArgumentException("Invalid shard iterator").asLeft
    else
      val iteratorTimeMillis = split.head
      val streamName = StreamName(split(1))
      val shardId = split(2)
      val sequenceNumber = SequenceNumber(split(3))

      (
        CommonValidations.validateStreamName(streamName),
        CommonValidations.validateShardId(shardId),
        CommonValidations.validateSequenceNumber(sequenceNumber),
        if Try(iteratorTimeMillis.toLong).isFailure then
          InvalidArgumentException(
            "Invalid ShardIterator, the time argument is not numeric"
          ).asLeft
        else Right(()),
        if Try(iteratorTimeMillis.toLong)
            .exists(x => x <= 0 || x > now.toEpochMilli)
        then
          InvalidArgumentException(
            "Invalid ShardIterator, the the time argument must be between 0 and now"
          ).asLeft
        else Right(()),
        if now.toEpochMilli - iteratorTimeMillis.toLong > 300000 then
          ExpiredIteratorException(
            "The shard iterator has expired. Shard iterators are only valid for 300 seconds"
          ).asLeft
        else Right(())
      ).mapN((_, _, _, _, _, _) =>
        ShardIteratorParts(streamName, shardId, sequenceNumber)
      )

final case class ShardIteratorParts(
    streamName: StreamName,
    shardId: String,
    sequenceNumber: SequenceNumber
)

object ShardIterator:

  private val iteratorPwdKey =
    BigInt(
      "1133a5a833666b49abf28c8ba302930f0b2fb240dccd43cf4dfbc0ca91f17751",
      16
    ).toByteArray
  private val iteratorPwdIv =
    BigInt("7bf139dbabbea2d9995d6fcae1dff7da", 16).toByteArray

  // See https://github.com/mhart/kinesalite/blob/master/db/index.js#L252
  def create(
      streamName: StreamName,
      shardId: String,
      sequenceNumber: SequenceNumber,
      now: Instant
  ): ShardIterator =
    val encryptString =
      (Vector.fill(14)("0").mkString + now.toEpochMilli)
        .takeRight(14) +
        s"/$streamName" +
        s"/$shardId" +
        s"/${sequenceNumber.value}/" +
        Vector.fill(37)("0").mkString

    val encryptedBytes = Array[Byte](0, 0, 0, 0, 0, 0, 0, 1) ++
      AES.encrypt(encryptString, iteratorPwdKey, iteratorPwdIv)

    ShardIterator(Base64.getEncoder.encodeToString(encryptedBytes))

  given shardIteratorCirceEncoder: Encoder[ShardIterator] =
    Encoder[String].contramap(_.value)

  given shardIteratorCirceDecoder: Decoder[ShardIterator] =
    Decoder[String].map(ShardIterator.apply)

  given shardIteratorEq: Eq[ShardIterator] = Eq.fromUniversalEquals
