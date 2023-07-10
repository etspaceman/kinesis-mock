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

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.Utils

class ConsumerArnSpec extends munit.ScalaCheckSuite {
  property("It should convert to a proper ARN format")(forAll {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      val creationTime = Utils.now
      val consumerArn = ConsumerArn(streamArn, consumerName, creationTime)

      val expected =
        s"arn:${streamArn.awsRegion.awsArnPiece}:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${creationTime.getEpochSecond}"

      (consumerArn.consumerArn == expected) :| s"Calculated: ${consumerArn}\nExpected: ${expected}"
  })

  property("It should be constructed by an ARN string")(forAll {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      val creationTime = Utils.now
      val expected =
        s"arn:${streamArn.awsRegion.awsArnPiece}:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${creationTime.getEpochSecond}"
      val consumerArn = ConsumerArn.fromArn(expected)

      (consumerArn.exists(
        _.consumerArn == expected
      )) :| s"Calculated: ${consumerArn}\nExpected: ${expected}\n"
  })
}
