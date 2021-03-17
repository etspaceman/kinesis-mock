package kinesis.mock.models

import enumeratum._

sealed abstract class AwsRegion(override val entryName: String)
    extends EnumEntry

object AwsRegion
    extends Enum[AwsRegion]
    with CirisEnum[AwsRegion]
    with CirceEnum[AwsRegion]
    with CatsEnum[AwsRegion] {
  override val values = findValues
  case object US_GOV_EAST_1 extends AwsRegion("us-gov-west-1")
  case object US_EAST_1 extends AwsRegion("us-east-1")
  case object US_EAST_2 extends AwsRegion("us-east-2")
  case object US_WEST_1 extends AwsRegion("us-west-1")
  case object US_WEST_2 extends AwsRegion("us-west-2")
  case object EU_WEST_1 extends AwsRegion("eu-west-1")
  case object EU_WEST_2 extends AwsRegion("eu-west-2")
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
  case object CN_NORTH_1 extends AwsRegion("cn-north-1")
  case object CN_NORTHWEST_1 extends AwsRegion("cn-northwest-1")
  case object CA_CENTRAL_1 extends AwsRegion("ca-central-1")
  case object ME_SOUTH_1 extends AwsRegion("me-south-1")
  case object AF_SOUTH_1 extends AwsRegion("af-south-1")
  case object US_ISO_EAST_1 extends AwsRegion("us-iso-east-1")
  case object US_ISOB_EAST_1 extends AwsRegion("us-isob-east-1")
  case object US_ISO_WEST_1 extends AwsRegion("us-iso-west-1")
}
