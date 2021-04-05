package kinesis.mock.models

import enumeratum._

sealed trait ShardLevelMetric extends EnumEntry

object ShardLevelMetric
    extends Enum[ShardLevelMetric]
    with CirceEnum[ShardLevelMetric]
    with CatsEnum[ShardLevelMetric] {
  override val values: IndexedSeq[ShardLevelMetric] = findValues

  case object IncomingBytes extends ShardLevelMetric
  case object IncomingRecords extends ShardLevelMetric
  case object OutgoingBytes extends ShardLevelMetric
  case object OutgoingRecords extends ShardLevelMetric
  case object WriteProvisionedThroughputExceeded extends ShardLevelMetric
  case object ReadProvisionedThroughputExceeded extends ShardLevelMetric
  case object IteratorAgeMilliseconds extends ShardLevelMetric
  case object ALL extends ShardLevelMetric
}
