package kinesis.mock

import cats.effect.IO
import fs2.io.net.tls.TLSContext
import fs2.io.net.Network

object TLS {
  def context(serviceConfig: KinesisMockServiceConfig): IO[TLSContext[IO]] =
    Network[IO].tlsContext.fromKeyStoreResource(
      "server.jks",
      serviceConfig.keyStorePassword.toCharArray(),
      serviceConfig.keyManagerPassword.toCharArray()
    )
}
