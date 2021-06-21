package kinesis.mock

import java.security.{KeyStore, Security}

import cats.effect.Sync
import javax.net.ssl.{KeyManagerFactory, SSLContext}

object ssl {
  def loadContextFromClasspath[F[_]](
      keyStorePassword: String,
      keyManagerPassword: String
  )(implicit F: Sync[F]): F[SSLContext] =
    F.delay {
      val ksStream = this.getClass.getResourceAsStream("/server.jks")
      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, keyStorePassword.toCharArray)
      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      )

      kmf.init(ks, keyManagerPassword.toCharArray)

      val context = SSLContext.getInstance("TLS")
      context.init(kmf.getKeyManagers, null, null) // scalafix:ok

      context
    }
}
