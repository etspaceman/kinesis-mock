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

package kinesis.mock.models

import enumeratum.*

sealed trait ShardIteratorType extends EnumEntry

object ShardIteratorType
    extends Enum[ShardIteratorType]
    with CirceEnum[ShardIteratorType]
    with CatsEnum[ShardIteratorType]:
  override val values: IndexedSeq[ShardIteratorType] = findValues

  case object AT_SEQUENCE_NUMBER extends ShardIteratorType
  case object AFTER_SEQUENCE_NUMBER extends ShardIteratorType
  case object AT_TIMESTAMP extends ShardIteratorType
  case object TRIM_HORIZON extends ShardIteratorType
  case object LATEST extends ShardIteratorType
