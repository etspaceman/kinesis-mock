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

import munit.FunSuite

class ResourceArnTests extends FunSuite:
  test("parses a stream ARN"):
    val arn =
      "arn:aws:kinesis:us-east-1:123456789012:stream/my-stream"
    val parsed = ResourceArn.fromString(arn)
    assert(parsed.isRight, parsed)
    parsed.foreach {
      case ResourceArn.Stream(sa) =>
        assertEquals(sa.streamName.streamName, "my-stream")
      case other => fail(s"expected Stream, got $other")
    }

  test("parses a consumer ARN"):
    val arn =
      "arn:aws:kinesis:us-east-1:123456789012:stream/my-stream/consumer/my-consumer:1700000000"
    val parsed = ResourceArn.fromString(arn)
    assert(parsed.isRight, parsed)
    parsed.foreach {
      case ResourceArn.Consumer(ca) =>
        assertEquals(ca.streamArn.streamName.streamName, "my-stream")
        assertEquals(ca.consumerName.consumerName, "my-consumer")
      case other => fail(s"expected Consumer, got $other")
    }

  test("rejects an unrecognized ARN"):
    val parsed = ResourceArn.fromString("not-an-arn")
    assert(parsed.isLeft, parsed)
