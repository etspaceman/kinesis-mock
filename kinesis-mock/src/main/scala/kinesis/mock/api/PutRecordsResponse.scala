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
package api

import cats.Eq
import io.circe

import kinesis.mock.models.EncryptionType

final case class PutRecordsResponse(
    encryptionType: EncryptionType,
    failedRecordCount: Int,
    records: Vector[PutRecordsResultEntry]
)

object PutRecordsResponse {
  given putRecordsResponseCirceEncoder: circe.Encoder[PutRecordsResponse] =
    circe.Encoder.forProduct3(
      "EncryptionType",
      "FailedRecordCount",
      "Records"
    )(x => (x.encryptionType, x.failedRecordCount, x.records))

  given putRecordsResponseCirceDecoder: circe.Decoder[PutRecordsResponse] =
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        failedRecordCount <- x.downField("FailedRecordCount").as[Int]
        records <- x.downField("Records").as[Vector[PutRecordsResultEntry]]
      } yield PutRecordsResponse(encryptionType, failedRecordCount, records)

  given putRecordsResponseEncoder: Encoder[PutRecordsResponse] =
    Encoder.derive
  given putRecordsResponseDecoder: Decoder[PutRecordsResponse] =
    Decoder.derive

  given putRecordsResponseEq: Eq[PutRecordsResponse] =
    Eq.fromUniversalEquals
}
