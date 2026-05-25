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

import kinesis.mock.instances.circe.given

final case class PutRecordsRequestEntry(
    data: Array[Byte],
    explicitHashKey: Option[String],
    partitionKey: String
)

object PutRecordsRequestEntry:
  given putRecordsRequestEntryCirceEncoder
      : circe.Encoder[PutRecordsRequestEntry] =
    circe.Encoder.forProduct3(
      "Data",
      "ExplicitHashKey",
      "PartitionKey"
    )(x => (x.data, x.explicitHashKey, x.partitionKey))

  given putRecordsRequestEntryCirceDecoder
      : circe.Decoder[PutRecordsRequestEntry] =
    x =>
      for
        data <- x.downField("Data").as[Array[Byte]]
        explicitHashKey <- x.downField("ExplicitHashKey").as[Option[String]]
        partitionKey <- x.downField("PartitionKey").as[String]
      yield PutRecordsRequestEntry(
        data,
        explicitHashKey,
        partitionKey
      )

  given putRecordsRequestEntryEncoder: Encoder[PutRecordsRequestEntry] =
    Encoder.derive
  given putRecordsRequestEntryDecoder: Decoder[PutRecordsRequestEntry] =
    Decoder.derive

  given Eq[PutRecordsRequestEntry] = (x, y) =>
    x.data.sameElements(y.data) &&
      x.explicitHashKey == y.explicitHashKey &&
      x.partitionKey == y.partitionKey
