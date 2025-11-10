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
package models

import enumeratum.*
import io.circe.{KeyDecoder, KeyEncoder}

sealed abstract class AwsRegion(
    override val entryName: String,
    val awsArnPiece: String = "aws"
) extends EnumEntry

object AwsRegion
    extends Enum[AwsRegion]
    with CirceEnum[AwsRegion]
    with CatsEnum[AwsRegion]
    with CirisEnum[AwsRegion]:
  override val values: IndexedSeq[AwsRegion] = findValues
  case object US_GOV_EAST_1 extends AwsRegion("us-gov-east-1", "aws-us-gov")
  case object US_GOV_WEST_1 extends AwsRegion("us-gov-west-1", "aws-us-gov")
  case object US_EAST_1 extends AwsRegion("us-east-1")
  case object US_EAST_2 extends AwsRegion("us-east-2")
  case object US_WEST_1 extends AwsRegion("us-west-1")
  case object US_WEST_2 extends AwsRegion("us-west-2")
  case object EU_WEST_1 extends AwsRegion("eu-west-1")
  case object EU_WEST_2 extends AwsRegion("eu-west-2")
  case object EU_WEST_3 extends AwsRegion("eu-west-3")
  case object EU_CENTRAL_1 extends AwsRegion("eu-central-1")
  case object EU_NORTH_1 extends AwsRegion("eu-north-1")
  case object EU_SOUTH_1 extends AwsRegion("eu-south-1")
  case object AP_EAST_1 extends AwsRegion("ap-east-1")
  case object AP_SOUTH_1 extends AwsRegion("ap-south-1")
  case object AP_SOUTHEAST_1 extends AwsRegion("ap-southeast-1")
  case object AP_SOUTHEAST_2 extends AwsRegion("ap-southeast-2")
  case object AP_NORTHEAST_1 extends AwsRegion("ap-northeast-1")
  case object AP_NORTHEAST_2 extends AwsRegion("ap-northeast-2")
  case object AP_NORTHEAST_3 extends AwsRegion("ap-northeast-3")
  case object SA_EAST_1 extends AwsRegion("sa-east-1")
  case object CN_NORTH_1 extends AwsRegion("cn-north-1", "aws-cn")
  case object CN_NORTHWEST_1 extends AwsRegion("cn-northwest-1", "aws-cn")
  case object CA_CENTRAL_1 extends AwsRegion("ca-central-1")
  case object ME_SOUTH_1 extends AwsRegion("me-south-1")
  case object AF_SOUTH_1 extends AwsRegion("af-south-1")
  case object US_ISO_EAST_1 extends AwsRegion("us-iso-east-1", "aws-iso")
  case object US_ISOB_EAST_1 extends AwsRegion("us-isob-east-1", "aws-iso-b")
  case object US_ISO_WEST_1 extends AwsRegion("us-iso-west-1", "aws-iso")

  given KeyEncoder[AwsRegion] =
    KeyEncoder.instance(_.entryName)
  given KeyDecoder[AwsRegion] =
    KeyDecoder.instance(AwsRegion.withNameOption)
