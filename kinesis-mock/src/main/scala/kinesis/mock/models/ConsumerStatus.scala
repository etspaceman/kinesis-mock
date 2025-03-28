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

sealed trait ConsumerStatus extends EnumEntry

object ConsumerStatus
    extends Enum[ConsumerStatus]
    with CirceEnum[ConsumerStatus]
    with CatsEnum[ConsumerStatus]:
  override val values: IndexedSeq[ConsumerStatus] = findValues

  case object CREATING extends ConsumerStatus
  case object DELETING extends ConsumerStatus
  case object ACTIVE extends ConsumerStatus
