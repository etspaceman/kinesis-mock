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

final case class ShardId(shardId: String, index: Int)

object ShardId {
  def create(index: Int): ShardId =
    ShardId("shardId-" + s"00000000000$index".takeRight(12), index)
  implicit val shardIdOrdering: Ordering[ShardId] = (x: ShardId, y: ShardId) =>
    x.index.compare(y.index)
}
