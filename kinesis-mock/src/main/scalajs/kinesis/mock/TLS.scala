/*
 * Copyright 2021-2026 io.github.etspaceman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock

import scala.scalajs.js

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.*
import fs2.io.net.Network
import fs2.io.net.tls.SecureContext
import fs2.io.net.tls.TLSContext
import fs2.text

object TLS:
  @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
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

@js.native
trait CertKey extends js.Object:
  def cert: String = js.native
  def key: String = js.native
