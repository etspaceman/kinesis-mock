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

import cats.Eq

import kinesis.mock.api.{PutRecordRequest, PutRecordsRequestEntry}
import kinesis.mock.models.KinesisRecord

final case class PutRecordResults(
    data: Array[Byte],
    partitionKey: String
)

object PutRecordResults:
  def fromKinesisRecord(x: KinesisRecord): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  def fromPutRecordsRequestEntry(x: PutRecordsRequestEntry): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  def fromPutRecordRequest(x: PutRecordRequest): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  given putRecordResultsEq: Eq[PutRecordResults] = (x, y) =>
    x.data.sameElements(y.data) && x.partitionKey == y.partitionKey
