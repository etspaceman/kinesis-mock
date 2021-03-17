package kinesis.mock.instances

import java.time.Instant

import enumeratum.scalacheck._
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.models._

object arbitrary {
  val streamArnGen: Gen[String] = for {
    streamName <- Gen.stringOfN(10, Gen.alphaChar)
    accountId <- Gen.stringOfN(12, Gen.numChar)
    region <- Arbitrary.arbitrary[AwsRegion].map(_.entryName)
  } yield s"arn:aws:kinesis:$region:$accountId:stream/$streamName"

  val nowGen: Gen[Instant] = Gen.delay(Gen.const(Instant.now()))

  implicit val sequenceNumberArbitrary: Arbitrary[SequenceNumber] = Arbitrary(
    Gen.option(Arbitrary.arbitrary[SequenceNumberConstant]).flatMap {
      case Some(constant) => SequenceNumber(constant.entryName)
      case None =>
        for {
          shardCreateTime <- nowGen.map(_.minusSeconds(300))
          shardIndex <- Gen.posNum[Int]
          seqIndex <- Gen.option(Gen.posNum[Int])
          seqTime <- Gen.option(nowGen)
        } yield SequenceNumber
          .create(shardCreateTime, shardIndex, None, seqIndex, seqTime)
    }
  )

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

  implicit val kineisRecordArbitrary: Arbitrary[KinesisRecord] = Arbitrary(
    for {
      approximateArrivalTimestamp <- nowGen
      data <- Arbitrary.arbitrary[Array[Byte]].suchThat(_.length < 1048576)
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      partitionKey <- Gen
        .choose(1, 256)
        .flatMap(size => Gen.stringOfN(size, Gen.alphaNumChar))
      sequenceNumber <- sequenceNumberArbitrary.arbitrary
    } yield KinesisRecord(
      approximateArrivalTimestamp,
      data,
      encryptionType,
      partitionKey,
      sequenceNumber
    )
  )

  implicit val sequenceNumberRangeArbitrary: Arbitrary[SequenceNumberRange] =
    Arbitrary(
      for {
        shardCreateTime <- nowGen.map(_.minusSeconds(300))
        shardIndex <- Gen.posNum[Int]
        startSeqIndex <- Gen.option(Gen.posNum[Int])
        startSeqTime <- Gen.option(nowGen)
        startingSequenceNumber = SequenceNumber.create(
          shardCreateTime,
          shardIndex,
          None,
          startSeqIndex,
          startSeqTime
        )
        endSeqTime <- nowGen
        endingSequenceNumber <- Gen.option(
          SequenceNumber.create(
            shardCreateTime,
            shardIndex,
            None,
            startSeqIndex.map(_ + 10000).orElse(Some(10000)),
            startSeqTime.map(_.plusSeconds(10000)).orElse(Some(endSeqTime))
          )
        )
      } yield SequenceNumberRange(endingSequenceNumber, startingSequenceNumber)
    )

  implicit val shardLevelMetricsArbitrary: Arbitrary[ShardLevelMetrics] =
    Arbitrary(
      Gen
        .listOf(Arbitrary.arbitrary[ShardLevelMetric])
        .map(ShardLevelMetrics.apply)
    )

  implicit val shardArbitrary: Arbitrary[Shard] = Arbitrary(
    for {
      shardIndex <- Gen.choose(100, 1000)
      shardId = Shard.shardId(shardIndex)
      createdAtTimestamp <- nowGen.map(_.minusSeconds(10000))
      adjacentParentShardId <- Gen.option(Gen.const(Shard.shardId(0)))
      parentShardId <- Gen.option(Gen.const(Shard.shardId(1)))
      hashKeyRange <- hashKeyRangeArbitrary.arbitrary
      sequenceNumberRange <- sequenceNumberRangeArbitrary.arbitrary
      closedTimestamp <- Gen
        .option(nowGen)
        .map(ts => sequenceNumberRange.endingSequenceNumber.flatMap(_ => ts))
    } yield Shard(
      adjacentParentShardId,
      closedTimestamp,
      createdAtTimestamp,
      hashKeyRange,
      parentShardId,
      sequenceNumberRange,
      shardId,
      shardIndex
    )
  )
}
