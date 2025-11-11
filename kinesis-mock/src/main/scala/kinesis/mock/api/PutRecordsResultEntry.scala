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

import kinesis.mock.models.SequenceNumber

final case class PutRecordsResultEntry(
    errorCode: Option[PutRecordsErrorCode],
    errorMessage: Option[String],
    sequenceNumber: Option[SequenceNumber],
    shardId: Option[String]
)

object PutRecordsResultEntry:
  given putRecordsResultEntryCirceEncoder
      : circe.Encoder[PutRecordsResultEntry] =
    circe.Encoder.forProduct4(
      "ErrorCode",
      "ErrorMessage",
      "SequenceNumber",
      "ShardId"
    )(x => (x.errorCode, x.errorMessage, x.sequenceNumber, x.shardId))

  given putRecordsResultEntryCirceDecoder
      : circe.Decoder[PutRecordsResultEntry] =
    x =>
      for
        errorCode <- x.downField("ErrorCode").as[Option[PutRecordsErrorCode]]
        errorMessage <- x.downField("ErrorMessage").as[Option[String]]
        sequenceNumber <- x
          .downField("SequenceNumber")
          .as[Option[SequenceNumber]]
        shardId <- x.downField("ShardId").as[Option[String]]
      yield PutRecordsResultEntry(
        errorCode,
        errorMessage,
        sequenceNumber,
        shardId
      )
  given putRecordsResultEntryEncoder: Encoder[PutRecordsResultEntry] =
    Encoder.derive
  given putRecordsResultEntryDecoder: Decoder[PutRecordsResultEntry] =
    Decoder.derive

  given Eq[PutRecordsResultEntry] =
    Eq.fromUniversalEquals
