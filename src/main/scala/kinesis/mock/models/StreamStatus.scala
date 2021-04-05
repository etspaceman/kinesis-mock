package kinesis.mock.models

import enumeratum._

sealed trait StreamStatus extends EnumEntry

object StreamStatus
    extends Enum[StreamStatus]
    with CirceEnum[StreamStatus]
    with CatsEnum[StreamStatus] {
  override val values: IndexedSeq[StreamStatus] = findValues
  case object ACTIVE extends StreamStatus
  case object UPDATING extends StreamStatus
  case object CREATING extends StreamStatus
  case object DELETING extends StreamStatus
}
