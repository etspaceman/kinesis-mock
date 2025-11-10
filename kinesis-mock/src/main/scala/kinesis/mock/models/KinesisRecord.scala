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

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe.{*, given}

final case class KinesisRecord(
    approximateArrivalTimestamp: Instant,
    data: Array[Byte],
    encryptionType: EncryptionType,
    partitionKey: String,
    sequenceNumber: SequenceNumber
):
  val size: Int = data.length +
    encryptionType.entryName.getBytes("UTF-8").length +
    partitionKey.getBytes("UTF-8").length +
    sequenceNumber.value.getBytes("UTF-8").length +
    approximateArrivalTimestamp.toString.getBytes("UTF-8").length

object KinesisRecord:
  def kinesisRecordCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[KinesisRecord] =
    circe.Encoder.forProduct5(
      "ApproximateArrivalTimestamp",
      "Data",
      "EncryptionType",
      "PartitionKey",
      "SequenceNumber"
    )(x =>
      (
        x.approximateArrivalTimestamp,
        x.data,
        x.encryptionType,
        x.partitionKey,
        x.sequenceNumber
      )
    )

  def kinesisRecordCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[KinesisRecord] =
    x =>
      for
        approximateArrivalTimestamp <- x
          .downField("ApproximateArrivalTimestamp")
          .as[Instant]
        data <- x.downField("Data").as[Array[Byte]]
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        partitionKey <- x.downField("PartitionKey").as[String]
        sequenceNumber <- x.downField("SequenceNumber").as[SequenceNumber]
      yield KinesisRecord(
        approximateArrivalTimestamp,
        data,
        encryptionType,
        partitionKey,
        sequenceNumber
      )

  given kinesisRecordEncoder: Encoder[KinesisRecord] = Encoder.instance(
    kinesisRecordCirceEncoder(using instantDoubleCirceEncoder),
    kinesisRecordCirceEncoder(using instantLongCirceEncoder)
  )

  given kinesisRecordDecoder: Decoder[KinesisRecord] = Decoder.instance(
    kinesisRecordCirceDecoder(using instantDoubleCirceDecoder),
    kinesisRecordCirceDecoder(using instantLongCirceDecoder)
  )

  given kinesisRecordEq: Eq[KinesisRecord] = (x, y) =>
    x.approximateArrivalTimestamp.getEpochSecond == y.approximateArrivalTimestamp.getEpochSecond &&
      x.data.sameElements(y.data) &&
      x.encryptionType == y.encryptionType &&
      x.partitionKey == y.partitionKey &&
      x.sequenceNumber == y.sequenceNumber
