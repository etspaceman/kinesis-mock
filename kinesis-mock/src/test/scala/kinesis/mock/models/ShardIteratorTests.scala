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

import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._

class ShardIteratorTests extends munit.ScalaCheckSuite {
  property("It should createt and parse correctly")(forAll {
    (
        streamName: StreamName,
        shardId: ShardId,
        sequenceNumber: SequenceNumber
    ) =>
      val iterator: ShardIterator = ShardIterator.create(
        streamName,
        shardId.shardId,
        sequenceNumber
      )
      val parsed = iterator.parse

      parsed.exists { parts =>
        parts.sequenceNumber == sequenceNumber &&
        parts.shardId == shardId.shardId &&
        parts.streamName == streamName
      } :| s"streamName: $streamName\n" +
        s"shardId: $shardId\n" +
        s"sequenceNumber: $sequenceNumber\n" +
        s"shardIterator: $iterator\n" +
        s"parsed: $parsed"
  })
}
