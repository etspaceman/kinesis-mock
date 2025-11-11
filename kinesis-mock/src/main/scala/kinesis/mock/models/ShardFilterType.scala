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

sealed trait ShardFilterType extends EnumEntry

object ShardFilterType
    extends Enum[ShardFilterType]
    with CirceEnum[ShardFilterType]
    with CatsEnum[ShardFilterType]:
  override val values: IndexedSeq[ShardFilterType] = findValues

  case object AFTER_SHARD_ID extends ShardFilterType
  case object AT_TRIM_HORIZON extends ShardFilterType
  case object FROM_TRIM_HORIZON extends ShardFilterType
  case object AT_LATEST extends ShardFilterType
  case object AT_TIMESTAMP extends ShardFilterType
  case object FROM_TIMESTAMP extends ShardFilterType
