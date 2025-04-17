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

package kinesis.mock.models

import org.scalacheck.effect.PropF

import kinesis.mock.Utils
import kinesis.mock.instances.arbitrary.given

class ConsumerArnSpec extends munit.ScalaCheckEffectSuite:
  test("It should convert to a proper ARN format")(PropF.forAllF {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      Utils.now.map { now =>
        val consumerArn = ConsumerArn(streamArn, consumerName, now)

        val expected =
          s"arn:${streamArn.awsRegion.awsArnPiece}:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${now.getEpochSecond}"

        assert(
          consumerArn.consumerArn == expected,
          s"Calculated: ${consumerArn}\nExpected: ${expected}"
        )
      }
  })

  test("It should be constructed by an ARN string")(PropF.forAllF {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      Utils.now.map { now =>
        val expected =
          s"arn:${streamArn.awsRegion.awsArnPiece}:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${now.getEpochSecond}"
        val consumerArn = ConsumerArn.fromArn(expected)

        assert(
          consumerArn.exists(
            _.consumerArn == expected
          ),
          s"Calculated: ${consumerArn}\nExpected: ${expected}\n"
        )
      }
  })
