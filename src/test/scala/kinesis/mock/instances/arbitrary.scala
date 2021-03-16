package kinesis.mock.instances

import org.scalacheck.{Arbitrary, Gen}
import enumeratum.scalacheck._

import kinesis.mock.models._
// import kinesis.mock.api._
import java.time.Instant

object arbitrary {
  val streamArnGen: Gen[String] = for {
    streamName <- Gen.stringOfN(10, Gen.alphaChar)
    accountId <- Gen.stringOfN(12, Gen.numChar)
    region <- Arbitrary.arbitrary[AwsRegion].map(_.entryName)
  } yield s"arn:aws:kinesis:$region:$accountId:stream/$streamName"

  val nowGen: Gen[Instant] = Gen.delay(Gen.const(Instant.now()))

  implicit val consumerArbitrary: Arbitrary[Consumer] = Arbitrary(
    for {
      streamArn <- streamArnGen
      consumerCreationTimestamp <- nowGen
      consumerName <- Gen.stringOfN(10, Gen.alphaChar)
      consumerArn =
        s"$streamArn/consumer/$consumerName:${consumerCreationTimestamp.getEpochSecond()}"
      consumerStatus <- Arbitrary.arbitrary[ConsumerStatus]
    } yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus
    )
  )

  implicit val hashKeyRangeArbitrary: Arbitrary[HashKeyRange] = Arbitrary(
    for {
      startingHashKey <- Gen.posNum[Int].map(BigInt.apply)
      endingHashKey <- Gen.posNum[Int].map(i => BigInt(i) + startingHashKey)
    } yield HashKeyRange(startingHashKey, endingHashKey)
  )
}
