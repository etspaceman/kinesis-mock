package kinesis.mock.models

import enumeratum._

sealed trait StreamMode extends EnumEntry

object StreamMode
    extends Enum[StreamMode]
    with CirceEnum[StreamMode]
    with CatsEnum[StreamMode] {
  override val values: IndexedSeq[StreamMode] = findValues

  case object PROVISIONED extends StreamMode
  case object ON_DEMAND extends StreamMode
}
