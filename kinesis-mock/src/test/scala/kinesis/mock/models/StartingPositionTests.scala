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
package models

import io.circe.parser
import io.circe.syntax.*

class StartingPositionTests extends munit.FunSuite:
  test("decode LATEST starting position (JSON)") {
    val json = """{"Type":"LATEST"}"""
    val res = parser.decode[StartingPosition](json)(using
      StartingPosition.startingPositionCirceDecoder(using
        instances.circe.instantBigDecimalCirceDecoder
      )
    )
    assert(res.isRight, res.toString)
    val sp = res.toOption.get
    assertEquals(sp.`type`, ShardIteratorType.LATEST)
    assertEquals(sp.sequenceNumber, None)
    assertEquals(sp.timestamp, None)
  }

  test("decode AT_SEQUENCE_NUMBER with SequenceNumber") {
    val json =
      """{"Type":"AT_SEQUENCE_NUMBER","SequenceNumber":"49590338271490256608559692538361571095921575989136588898"}"""
    val res = parser.decode[StartingPosition](json)(using
      StartingPosition.startingPositionCirceDecoder(using
        instances.circe.instantBigDecimalCirceDecoder
      )
    )
    assert(res.isRight, res.toString)
    val sp = res.toOption.get
    assertEquals(sp.`type`, ShardIteratorType.AT_SEQUENCE_NUMBER)
    assert(sp.sequenceNumber.isDefined)
  }

  test("decode AT_TIMESTAMP with Timestamp (seconds as Double)") {
    val json = """{"Type":"AT_TIMESTAMP","Timestamp":1500000000}"""
    val res = parser.decode[StartingPosition](json)(using
      StartingPosition.startingPositionCirceDecoder(using
        instances.circe.instantBigDecimalCirceDecoder
      )
    )
    assert(res.isRight, res.toString)
    assert(res.toOption.exists(_.timestamp.isDefined))
  }

  test("round-trip through JSON encoder/decoder") {
    val sp = StartingPosition(
      ShardIteratorType.LATEST,
      None,
      None
    )
    val encoder = StartingPosition.startingPositionCirceEncoder(using
      instances.circe.instantBigDecimalCirceEncoder
    )
    val decoder = StartingPosition.startingPositionCirceDecoder(using
      instances.circe.instantBigDecimalCirceDecoder
    )
    val json = sp.asJson(using encoder).noSpaces
    val decoded = parser.decode[StartingPosition](json)(using decoder)
    assertEquals(decoded, Right(sp))
  }
