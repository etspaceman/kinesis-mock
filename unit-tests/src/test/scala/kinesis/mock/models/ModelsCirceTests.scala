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

import enumeratum.scalacheck.*

import kinesis.mock.instances.arbitrary.given

class ModelsCirceTests extends CirceTests:
  identityLawTest[AwsRegion]
  identityLawTest[ConsumerName]
  identityLawTest[ConsumerStatus]
  identityLawTest[ConsumerArn]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[ScalingType]
  identityLawTest[ShardFilterType]
  identityLawTest[ShardIterator]
  identityLawTest[ShardLevelMetric]
  identityLawTest[ShardLevelMetrics]
  identityLawTest[ShardSummary]
  identityLawTest[SequenceNumber]
  identityLawTest[SequenceNumberConstant]
  identityLawTest[SequenceNumberRange]
  identityLawTest[Streams]
  identityLawTest[StreamArn]
  identityLawTest[StreamData]
  identityLawTest[StreamMode]
  identityLawTest[StreamModeDetails]
  identityLawTest[StreamName]
  identityLawTest[StreamStatus]
  identityLawTest[TagList]
  identityLawTest[TagListEntry]
  identityLawTest[Tags]
