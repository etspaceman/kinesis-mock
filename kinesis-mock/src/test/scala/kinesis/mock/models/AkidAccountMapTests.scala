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

package kinesis.mock.models

class AkidAccountMapTests extends munit.FunSuite:

  test("empty parses to empty map"):
    assertEquals(AkidAccountMap.parse(""), Right(AkidAccountMap.empty))

  test("single AKID:account pair parses"):
    val expected = AkidAccountMap(
      Map("AKIAONE" -> AwsAccountId("111111111111"))
    )
    assertEquals(AkidAccountMap.parse("AKIAONE:111111111111"), Right(expected))

  test("multiple pairs separated by comma parse"):
    val expected = AkidAccountMap(
      Map(
        "AKIAONE" -> AwsAccountId("111111111111"),
        "AKIATWO" -> AwsAccountId("222222222222")
      )
    )
    assertEquals(
      AkidAccountMap.parse("AKIAONE:111111111111,AKIATWO:222222222222"),
      Right(expected)
    )

  test("whitespace around entries is tolerated"):
    val expected = AkidAccountMap(
      Map("AKIAONE" -> AwsAccountId("111111111111"))
    )
    assertEquals(
      AkidAccountMap.parse(" AKIAONE : 111111111111 "),
      Right(expected)
    )

  test("malformed entry returns Left"):
    assert(AkidAccountMap.parse("AKIAONE").isLeft)
    assert(AkidAccountMap.parse("AKIAONE:111:extra").isLeft)
    assert(AkidAccountMap.parse(":111111111111").isLeft)
    assert(AkidAccountMap.parse("AKIAONE:").isLeft)

  test("resolve returns mapped account when AKID is known"):
    val map = AkidAccountMap(
      Map("AKIAONE" -> AwsAccountId("111111111111"))
    )
    assertEquals(
      map.resolve("AKIAONE", AwsAccountId("000000000000")),
      AwsAccountId("111111111111")
    )

  test("resolve falls back to default when AKID is unknown"):
    val map = AkidAccountMap(
      Map("AKIAONE" -> AwsAccountId("111111111111"))
    )
    assertEquals(
      map.resolve("UNKNOWN", AwsAccountId("000000000000")),
      AwsAccountId("000000000000")
    )

  test("empty map always returns default"):
    assertEquals(
      AkidAccountMap.empty.resolve("anything", AwsAccountId("000000000000")),
      AwsAccountId("000000000000")
    )
