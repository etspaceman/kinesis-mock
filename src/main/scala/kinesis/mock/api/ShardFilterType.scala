package kinesis.mock.api

import enumeratum._

sealed trait ShardFilterType extends EnumEntry

object ShardFilterType
    extends Enum[ShardFilterType]
    with CirceEnum[ShardFilterType] {
  override val values = findValues

  case object AFTER_SHARD_ID extends ShardFilterType
  case object AT_TRIM_HORIZON extends ShardFilterType
  case object FROM_TRIM_HORIZON extends ShardFilterType
  case object AT_LATEST extends ShardFilterType
  case object AT_TIMESTAMP extends ShardFilterType
  case object FROM_TIMESTAMP extends ShardFilterType
}
