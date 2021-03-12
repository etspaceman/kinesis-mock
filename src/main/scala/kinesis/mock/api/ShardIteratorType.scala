package kinesis.mock.api

import enumeratum._

sealed trait ShardIteratorType extends EnumEntry

object ShardIteratorType
    extends Enum[ShardIteratorType]
    with CirceEnum[ShardIteratorType] {
  override val values = findValues

  case object AT_SEQUENCE_NUMBER extends ShardIteratorType
  case object AFTER_SEQUENCE_NUMBER extends ShardIteratorType
  case object AT_TIMESTAMP extends ShardIteratorType
  case object TRIM_HORIZON extends ShardIteratorType
  case object LATEST extends ShardIteratorType
}
