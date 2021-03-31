package kinesis.mock.models

import pureconfig.ConfigReader

final case class AwsAccountId(accountId: String) {
  override def toString: String = accountId
}

object AwsAccountId {
  implicit val awsAccountIdConfigReader: ConfigReader[AwsAccountId] =
    ConfigReader[String].map(AwsAccountId.apply)
}
