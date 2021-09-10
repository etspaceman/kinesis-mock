package kinesis.mock.models

import cats.kernel.Eq
import io.circe.Encoder
import pureconfig.ConfigReader

final case class AwsAccountId(accountId: String) {
  override def toString: String = accountId
}

object AwsAccountId {
  implicit val awsAccountIdCirceEncoder: Encoder[AwsAccountId] =
    Encoder[String].contramap(_.accountId)
  implicit val awsAccountIdConfigReader: ConfigReader[AwsAccountId] =
    ConfigReader[String].map(AwsAccountId.apply)
  implicit val awsAccountIdEq: Eq[AwsAccountId] = Eq.fromUniversalEquals
}
