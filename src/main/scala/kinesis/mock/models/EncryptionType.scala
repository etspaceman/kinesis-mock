package kinesis.mock.models

import enumeratum._

sealed trait EncryptionType extends EnumEntry

object EncryptionType {
  case object NONE extends EncryptionType
  case object KMS extends EncryptionType
}
