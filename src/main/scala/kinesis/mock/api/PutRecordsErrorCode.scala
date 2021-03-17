package kinesis.mock.api

import enumeratum._

sealed trait PutRecordsErrorCode extends EnumEntry

object PutRecordsErrorCode
    extends Enum[PutRecordsErrorCode]
    with CirceEnum[PutRecordsErrorCode]
    with CatsEnum[PutRecordsErrorCode] {
  override val values = findValues

  case object InternalFailure extends PutRecordsErrorCode
  case object ProvisionedThroughputExceededException extends PutRecordsErrorCode
}
