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

import kinesis.mock.models.*

final case class PutRecordResponse(
    encryptionType: EncryptionType,
    sequenceNumber: SequenceNumber,
    shardId: String
)

object PutRecordResponse:
  given putRecordResponseCirceEncoder: circe.Encoder[PutRecordResponse] =
    circe.Encoder.forProduct3("EncryptionType", "SequenceNumber", "ShardId")(
      x => (x.encryptionType, x.sequenceNumber, x.shardId)
    )

  given putRecordResponseCirceDecoder: circe.Decoder[PutRecordResponse] =
    x =>
      for
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        sequenceNumber <- x.downField("SequenceNumber").as[SequenceNumber]
        shardId <- x.downField("ShardId").as[String]
      yield PutRecordResponse(encryptionType, sequenceNumber, shardId)

  given putRecordResponseEncoder: Encoder[PutRecordResponse] =
    Encoder.derive
  given putRecordResponseDecoder: Decoder[PutRecordResponse] =
    Decoder.derive

  given putRecordResponseEq: Eq[PutRecordResponse] =
    Eq.fromUniversalEquals
