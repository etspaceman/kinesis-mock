package kinesis.mock

import scala.scalajs.js

import cats.effect.IO
import cats.syntax.all._
import fs2.io.file._
import fs2.io.net.Network
import fs2.io.net.tls.SecureContext
import fs2.io.net.tls.TLSContext
import fs2.text

object TLS {
  def context(serviceConfig: KinesisMockServiceConfig): IO[TLSContext[IO]] =
    Files[IO]
      .readAll(Path(serviceConfig.certPath))
      .through(text.utf8.decode)
      .compile
      .string
      .flatMap(s =>
        IO(js.JSON.parse(s).asInstanceOf[js.Dictionary[CertKey]]("server"))
      )
      .map { certKey =>
        Network[IO].tlsContext.fromSecureContext(
          SecureContext(
            ca = List(certKey.cert.asRight).some,
            cert = List(certKey.cert.asRight).some,
            key = List(
              SecureContext
                .Key(certKey.key.asRight, serviceConfig.certPassword.some)
            ).some
          )
        )
      }
}

@js.native
trait CertKey extends js.Object {
  def cert: String = js.native
  def key: String = js.native
}
