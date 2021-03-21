package kinesis.mock
package models

import enumeratum.scalacheck._

import kinesis.mock.instances.arbitrary._

class ModelsCirceTests extends CirceTests {
  identityLawTest[AwsRegion]
  identityLawTest[Consumer]
  identityLawTest[ConsumerStatus]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[KinesisRecord]
  identityLawTest[ShardLevelMetric]
  identityLawTest[ShardLevelMetrics]
  identityLawTest[SequenceNumber]
  identityLawTest[SequenceNumberConstant]
  identityLawTest[SequenceNumberRange]
  identityLawTest[Shard]
  identityLawTest[StreamName]
  identityLawTest[StreamStatus]
}
