package kinesis.mock.models

import enumeratum._

sealed trait SequenceNumberConstant extends EnumEntry

object SequenceNumberConstant
    extends Enum[SequenceNumberConstant]
    with CirceEnum[SequenceNumberConstant]
    with CatsEnum[SequenceNumberConstant] {
  override val values = findValues

  case object AT_TIMESTAMP extends SequenceNumberConstant
  case object LATEST extends SequenceNumberConstant
  case object TRIM_HORIZON extends SequenceNumberConstant
  case object SHARD_END extends SequenceNumberConstant
}
