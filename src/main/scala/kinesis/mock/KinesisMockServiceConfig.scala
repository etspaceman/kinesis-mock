package kinesis.mock

import cats.syntax.all._
import ciris._

final case class KinesisMockServiceConfig(
    port: Int,
    keyStorePassword: String,
    keyManagerPassword: String
)

object KinesisMockServiceConfig {
  def read: ConfigValue[KinesisMockServiceConfig] =
    (
      env("KINESIS_MOCK_PORT").as[Int].default(4567),
      env("KINESIS_MOCK_KEYSTORE_PASSWORD").secret.default(
        Secret("kinesisMock")
      ),
      env("KINESIS_MOCK_KEYMANAGER_PASSWORD").secret.default(
        Secret("kinesisMock")
      )
    ).mapN((port, keystorePassword, keymanagerPassword) =>
      KinesisMockServiceConfig(
        port,
        keystorePassword.value,
        keymanagerPassword.value
      )
    )
}
