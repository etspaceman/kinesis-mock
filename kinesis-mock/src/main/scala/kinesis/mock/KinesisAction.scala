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

import cats.syntax.all._
import enumeratum._
import org.http4s.{ParseFailure, QueryParamDecoder}

sealed trait KinesisAction extends EnumEntry

object KinesisAction extends Enum[KinesisAction] {
  override val values: IndexedSeq[KinesisAction] = findValues

  case object AddTagsToStream extends KinesisAction
  case object CreateStream extends KinesisAction
  case object DecreaseStreamRetentionPeriod extends KinesisAction
  case object DeleteStream extends KinesisAction
  case object DeregisterStreamConsumer extends KinesisAction
  case object DescribeLimits extends KinesisAction
  case object DescribeStream extends KinesisAction
  case object DescribeStreamConsumer extends KinesisAction
  case object DescribeStreamSummary extends KinesisAction
  case object DisableEnhancedMonitoring extends KinesisAction
  case object EnableEnhancedMonitoring extends KinesisAction
  case object GetRecords extends KinesisAction
  case object GetShardIterator extends KinesisAction
  case object IncreaseStreamRetentionPeriod extends KinesisAction
  case object ListShards extends KinesisAction
  case object ListStreamConsumers extends KinesisAction
  case object ListStreams extends KinesisAction
  case object ListTagsForStream extends KinesisAction
  case object MergeShards extends KinesisAction
  case object PutRecord extends KinesisAction
  case object PutRecords extends KinesisAction
  case object RegisterStreamConsumer extends KinesisAction
  case object RemoveTagsFromStream extends KinesisAction
  case object SplitShard extends KinesisAction
  case object StartStreamEncryption extends KinesisAction
  case object StopStreamEncryption extends KinesisAction
  case object SubscribeToShard extends KinesisAction
  case object UpdateShardCount extends KinesisAction
  case object UpdateStreamMode extends KinesisAction

  given kinesisActionQueryParamDecoder: QueryParamDecoder[KinesisAction] =
    QueryParamDecoder[String].emap(x =>
      KinesisAction
        .withNameEither(x)
        .leftMap(e => ParseFailure(e.getMessage, ""))
    )
}
