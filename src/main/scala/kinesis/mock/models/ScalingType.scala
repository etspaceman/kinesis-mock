package kinesis.mock.models

import enumeratum._

sealed trait ScalingType extends EnumEntry

object ScalingType
    extends Enum[ScalingType]
    with CirceEnum[ScalingType]
    with CatsEnum[ScalingType] {
  override val values: IndexedSeq[ScalingType] = findValues
  case object UNIFORM_SCALING extends ScalingType
}
