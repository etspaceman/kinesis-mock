package kinesis.mock

import ciris._

final case class FunctionalTestConfig(servicePort: Int)

object FunctionalTestConfig {
  def read: ConfigValue[Effect, FunctionalTestConfig] =
    for {
      servicePort <- env("SERVICE_PORT").default("4567").as[Int]
    } yield FunctionalTestConfig(servicePort)
}
