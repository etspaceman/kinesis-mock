package kinesis.mock
package models

import enumeratum.scalacheck._

import kinesis.mock.instances.arbitrary._

class ModelsCirceTests extends CirceTests {
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
}
