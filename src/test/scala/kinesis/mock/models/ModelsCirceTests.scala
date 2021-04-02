package kinesis.mock
package models

import enumeratum.scalacheck._

import kinesis.mock.instances.arbitrary._

class ModelsCirceTests extends CirceTests {
  identityLawTest[AwsRegion]
  identityLawTest[Consumer]
  identityLawTest[ConsumerName]
  identityLawTest[ConsumerStatus]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[KinesisRecord]
  identityLawTest[ScalingType]
  identityLawTest[ShardFilter]
  identityLawTest[ShardFilterType]
  identityLawTest[ShardIterator]
  identityLawTest[ShardLevelMetric]
  identityLawTest[ShardLevelMetrics]
  identityLawTest[ShardSummary]
  identityLawTest[SequenceNumber]
  identityLawTest[SequenceNumberConstant]
  identityLawTest[SequenceNumberRange]
  identityLawTest[Shard]
  identityLawTest[StreamDescription]
  identityLawTest[StreamDescriptionSummary]
  identityLawTest[StreamName]
  identityLawTest[StreamStatus]
  identityLawTest[TagList]
  identityLawTest[TagListEntry]
  identityLawTest[Tags]
}
