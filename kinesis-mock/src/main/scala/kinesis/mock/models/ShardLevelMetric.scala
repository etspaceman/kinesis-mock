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

sealed trait ShardLevelMetric extends EnumEntry

object ShardLevelMetric
    extends Enum[ShardLevelMetric]
    with CirceEnum[ShardLevelMetric]
    with CatsEnum[ShardLevelMetric]:
  override val values: IndexedSeq[ShardLevelMetric] = findValues

  case object IncomingBytes extends ShardLevelMetric
  case object IncomingRecords extends ShardLevelMetric
  case object OutgoingBytes extends ShardLevelMetric
  case object OutgoingRecords extends ShardLevelMetric
  case object WriteProvisionedThroughputExceeded extends ShardLevelMetric
  case object ReadProvisionedThroughputExceeded extends ShardLevelMetric
  case object IteratorAgeMilliseconds extends ShardLevelMetric
  case object ALL extends ShardLevelMetric
