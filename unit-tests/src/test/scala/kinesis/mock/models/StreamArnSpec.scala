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

import enumeratum.scalacheck.*
import org.scalacheck.Prop.*

import kinesis.mock.instances.arbitrary.given

class StreamArnSpec extends munit.ScalaCheckSuite:
  property("It should convert to a proper ARN format")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streamArn = StreamArn(awsRegion, streamName, awsAccountId)

      val expected =
        s"arn:${awsRegion.awsArnPiece}:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName"

      (streamArn.streamArn == expected) :| s"Calculated: ${streamArn}\nExpected: ${expected}"
  })
