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

import kinesis.mock.models.*

class KinesisMockRoutesAkidTests extends munit.FunSuite:

  private val default = AwsAccountId("000000000000")
  private val map = AkidAccountMap(
    Map(
      "AKIAONE" -> AwsAccountId("111111111111"),
      "AKIATWO" -> AwsAccountId("222222222222")
    )
  )

  test("resolveAccountId returns mapped account for known AKID"):
    val credential = "AKIAONE/20260529/us-east-1/kinesis/aws4_request"
    assertEquals(
      KinesisMockRoutes.resolveAccountId(credential, map, default),
      Some(AwsAccountId("111111111111"))
    )

  test("resolveAccountId falls back to default for unknown AKID"):
    val credential = "UNKNOWNKEY/20260529/us-east-1/kinesis/aws4_request"
    assertEquals(
      KinesisMockRoutes.resolveAccountId(credential, map, default),
      Some(default)
    )

  test("resolveAccountId returns None for empty credential"):
    assertEquals(
      KinesisMockRoutes.resolveAccountId("", map, default),
      None
    )

  test("resolveAccountId handles credential without slashes"):
    assertEquals(
      KinesisMockRoutes.resolveAccountId("AKIAONE", map, default),
      Some(AwsAccountId("111111111111"))
    )
