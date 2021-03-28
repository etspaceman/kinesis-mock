package kinesis.mock.models

import ciris.ConfigDecoder

final case class AwsAccountId(accountId: String) {
  override def toString: String = accountId
}

object AwsAccountId {
  implicit val awsAccountIdConfigDecoder: ConfigDecoder[String, AwsAccountId] =
    ConfigDecoder[String].map(AwsAccountId.apply)
}
