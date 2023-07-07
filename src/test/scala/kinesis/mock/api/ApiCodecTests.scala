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

import kinesis.mock.instances.arbitrary._

class ApiCodecTests extends CodecTests {
  identityLawTest[AddTagsToStreamRequest]
  identityLawTest[CreateStreamRequest]
  identityLawTest[DecreaseStreamRetentionPeriodRequest]
  identityLawTest[DeleteStreamRequest]
  identityLawTest[DeregisterStreamConsumerRequest]
  identityLawTest[DescribeLimitsResponse]
  identityLawTest[DescribeStreamConsumerRequest]
  identityLawTest[DescribeStreamRequest]
  identityLawTest[DescribeStreamResponse]
  identityLawTest[DescribeStreamSummaryRequest]
  identityLawTest[DescribeStreamSummaryResponse]
  identityLawTest[DisableEnhancedMonitoringRequest]
  identityLawTest[DisableEnhancedMonitoringResponse]
  identityLawTest[EnableEnhancedMonitoringRequest]
  identityLawTest[EnableEnhancedMonitoringResponse]
  identityLawTest[GetRecordsRequest]
  identityLawTest[GetRecordsResponse]
  identityLawTest[GetShardIteratorRequest]
  identityLawTest[GetShardIteratorResponse]
  identityLawTest[IncreaseStreamRetentionPeriodRequest]
  identityLawTest[ListShardsRequest]
  identityLawTest[ListShardsResponse]
  identityLawTest[ListStreamConsumersRequest]
  identityLawTest[ListStreamConsumersResponse]
  identityLawTest[ListStreamsRequest]
  identityLawTest[ListStreamsResponse]
  identityLawTest[ListTagsForStreamRequest]
  identityLawTest[ListTagsForStreamResponse]
  identityLawTest[MergeShardsRequest]
  identityLawTest[PutRecordRequest]
  identityLawTest[PutRecordResponse]
  identityLawTest[PutRecordsRequest]
  identityLawTest[PutRecordsRequestEntry]
  identityLawTest[PutRecordsResponse]
  identityLawTest[PutRecordsResultEntry]
  identityLawTest[RegisterStreamConsumerRequest]
  identityLawTest[RegisterStreamConsumerResponse]
  identityLawTest[SplitShardRequest]
  identityLawTest[StartStreamEncryptionRequest]
  identityLawTest[StopStreamEncryptionRequest]
  identityLawTest[UpdateShardCountRequest]
  identityLawTest[UpdateShardCountResponse]
  identityLawTest[UpdateStreamModeRequest]
}
