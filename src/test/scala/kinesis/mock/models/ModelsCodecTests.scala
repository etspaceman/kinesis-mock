package kinesis.mock
package models

import enumeratum.scalacheck._

import kinesis.mock.instances.arbitrary._

class ModelsCodecTests extends CodecTests {
  identityLawTest[Consumer]
  identityLawTest[ConsumerSummary]
  identityLawTest[HashKeyRange]
  identityLawTest[KinesisRecord]
  identityLawTest[Shard]
  identityLawTest[ShardLevelMetrics]
  identityLawTest[ShardSummary]
  identityLawTest[SequenceNumberRange]
  identityLawTest[StreamDescription]
  identityLawTest[StreamDescriptionSummary]
  identityLawTest[TagListEntry]
}
