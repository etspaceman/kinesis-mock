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

  test("resolveAccountId returns the account when the AKID is a 12-digit id"):
    val credential = "111111111111/20260529/us-east-1/kinesis/aws4_request"
    assertEquals(
      KinesisMockRoutes.resolveAccountId(credential),
      Some(AwsAccountId("111111111111"))
    )

  test("resolveAccountId handles a bare 12-digit AKID without slashes"):
    assertEquals(
      KinesisMockRoutes.resolveAccountId("222222222222"),
      Some(AwsAccountId("222222222222"))
    )

  test("resolveAccountId returns None for an opaque access key"):
    val credential =
      "mock-kinesis-access-key/20260529/us-east-1/kinesis/aws4_request"
    assertEquals(KinesisMockRoutes.resolveAccountId(credential), None)

  test("resolveAccountId returns None for an AWS-format access key"):
    val credential =
      "AKIAIOSFODNN7EXAMPLE/20260529/us-east-1/kinesis/aws4_request"
    assertEquals(KinesisMockRoutes.resolveAccountId(credential), None)

  test("resolveAccountId returns None for a non-12-digit number"):
    assertEquals(KinesisMockRoutes.resolveAccountId("11111111111"), None)
    assertEquals(KinesisMockRoutes.resolveAccountId("1111111111111"), None)

  test("resolveAccountId returns None for an empty credential"):
    assertEquals(KinesisMockRoutes.resolveAccountId(""), None)
