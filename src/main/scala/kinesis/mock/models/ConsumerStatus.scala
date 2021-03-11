package kinesis.mock.models

import enumeratum._

sealed trait ConsumerStatus extends EnumEntry

object ConsumerStatus extends Enum[ConsumerStatus] {
  override val values = findValues

  case object CREATING extends ConsumerStatus
  case object DELETING extends ConsumerStatus
  case object ACTIVE extends ConsumerStatus
}
