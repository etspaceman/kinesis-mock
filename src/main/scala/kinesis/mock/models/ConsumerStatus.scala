package kinesis.mock.models

import enumeratum._

sealed trait ConsumerStatus extends EnumEntry

object ConsumerStatus
    extends Enum[ConsumerStatus]
    with CirceEnum[ConsumerStatus]
    with CatsEnum[ConsumerStatus] {
  override val values: IndexedSeq[ConsumerStatus] = findValues

  case object CREATING extends ConsumerStatus
  case object DELETING extends ConsumerStatus
  case object ACTIVE extends ConsumerStatus
}
