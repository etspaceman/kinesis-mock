/*
 * Copyright 2021-2023 Typelevel
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

import ciris._
import com.comcast.ip4s.Port

import kinesis.mock.instances.ciris._

final case class KinesisMockServiceConfig(
    tlsPort: Port,
    plainPort: Port,
    keyStorePassword: String,
    keyManagerPassword: String,
    certPassword: String,
    certPath: String
)

object KinesisMockServiceConfig {
  def read: ConfigValue[Effect, KinesisMockServiceConfig] = for {
    tlsPort <- env("KINESIS_MOCK_TLS_PORT").default("4567").as[Port]
    plainPort <- env("KINESIS_MOCK_PLAIN_PORT").default("4568").as[Port]
    keyStorePassword <- env("KINESIS_MOCK_KEYSTORE_PASSWORD").default(
      "kinesisMock"
    )
    keyManagerPassword <- env("KINESIS_MOCK_KEYMANAGER_PASSWORD").default(
      "kinesisMock"
    )
    certPassword <- env("KINESIS_MOCK_CERT_PASSWORD").default(
      "password"
    )
    certPath <- env("KINESIS_MOCK_CERT_PATH").default(
      "server.json"
    )
  } yield KinesisMockServiceConfig(
    tlsPort,
    plainPort,
    keyStorePassword,
    keyManagerPassword,
    certPassword,
    certPath
  )
}
