package kinesis.mock.instances

import java.time.Instant

import enumeratum.scalacheck._
import org.scalacheck.{Arbitrary, Gen}
import wolfendale.scalacheck.regexp.RegexpGen

import kinesis.mock.api._
import kinesis.mock.models._

object arbitrary {

  def arnPrefixGen(service: String, part: String): Gen[String] = for {
    accountId <- Gen.stringOfN(12, Gen.numChar)
    region <- Arbitrary.arbitrary[AwsRegion].map(_.entryName)
  } yield s"arn:aws:$service:$region:$accountId:$part/"

  def arnGen(service: String, part: String, value: String): Gen[String] =
    arnPrefixGen(service, part).map(arnPrefix => s"$arnPrefix$value")

  val streamNameGen: Gen[String] =
    Gen
      .choose(1, 128)
      .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))

  val streamArnGen: Gen[String] = for {
    streamName <- streamNameGen
    arnPrefix <- arnGen("kinesis", "stream", streamName)
  } yield s"$arnPrefix/$streamName"

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

  val consuemrNameGen: Gen[String] = Gen
    .choose(1, 128)
    .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))

  val consumerArnGen: Gen[String] = for {
    streamArn <- streamArnGen
    consumerName <- consuemrNameGen
    consumerCreationTimestamp <- nowGen
  } yield s"$streamArn/consumer/$consumerName:${consumerCreationTimestamp.getEpochSecond()}"

  implicit val consumerArbitrary: Arbitrary[Consumer] = Arbitrary(
    for {
      streamArn <- streamArnGen
      consumerCreationTimestamp <- nowGen
      consumerName <- consuemrNameGen
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
        .containerOf[Set, ShardLevelMetric](
          Gen.oneOf(
            ShardLevelMetric.IncomingBytes,
            ShardLevelMetric.IncomingRecords,
            ShardLevelMetric.OutgoingBytes,
            ShardLevelMetric.OutgoingRecords,
            ShardLevelMetric.WriteProvisionedThroughputExceeded,
            ShardLevelMetric.ReadProvisionedThroughputExceeded,
            ShardLevelMetric.IteratorAgeMilliseconds
          )
        )
        .map(x => ShardLevelMetrics(x.toList))
    )

  def shardGen(shardIndex: Int): Gen[Shard] = for {
    shardId <- Gen.const(Shard.shardId(shardIndex))
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

  implicit val shardArbitrary: Arbitrary[Shard] = Arbitrary(
    Gen.choose(100, 1000).flatMap(index => shardGen(index))
  )

  implicit val shardSummaryArbitrary: Arbitrary[ShardSummary] = Arbitrary(
    shardArbitrary.arbitrary.map(ShardSummary.fromShard)
  )

  val tagKeyGen: Gen[String] = Gen
    .choose(1, 128)
    .flatMap(size =>
      Gen
        .resize(size, RegexpGen.from("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$"))
        .suchThat(x => !x.startsWith("aws:"))
    )

  val tagValueGen: Gen[String] = Gen
    .choose(0, 255)
    .flatMap(size =>
      Gen.resize(size, RegexpGen.from("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$"))
    )

  val tagsGen: Gen[Map[String, String]] = Gen
    .choose(0, 10)
    .flatMap(size => Gen.mapOfN(size, Gen.zip(tagKeyGen, tagValueGen)))

  implicit val addTagsToStreamRequestArbitrary
      : Arbitrary[AddTagsToStreamRequest] = Arbitrary(
    for {
      streamName <- streamNameGen
      tags <- tagsGen
    } yield AddTagsToStreamRequest(streamName, tags)
  )

  implicit val createStreamRequestArb: Arbitrary[CreateStreamRequest] =
    Arbitrary(
      for {
        shardCount <- Gen.choose(1, 1000)
        streamName <- streamNameGen
      } yield CreateStreamRequest(shardCount, streamName)
    )

  val retentionPeriodHoursGen: Gen[Int] = Gen.choose(
    StreamData.minRetentionPeriod.toHours.toInt,
    StreamData.maxRetentionPeriod.toHours.toInt
  )

  implicit val decreaseStreamRetentionRequestArb
      : Arbitrary[DecreaseStreamRetentionRequest] = Arbitrary(
    for {
      retentionPeriodHours <- retentionPeriodHoursGen
      streamName <- streamNameGen
    } yield DecreaseStreamRetentionRequest(retentionPeriodHours, streamName)
  )

  implicit val deleteStreamRequestArb: Arbitrary[DeleteStreamRequest] =
    Arbitrary(
      for {
        streamName <- streamNameGen
        enforceConsumerDeletion <- Gen.option(Arbitrary.arbitrary[Boolean])
      } yield DeleteStreamRequest(streamName, enforceConsumerDeletion)
    )

  implicit val deregisterStreamConsumeRequestArb
      : Arbitrary[DeregisterStreamConsumerRequest] = Arbitrary(
    for {
      consumerArn <- Gen.option(consumerArnGen)
      consumerName <-
        if (consumerArn.isEmpty) consuemrNameGen.map(x => Some(x))
        else Gen.const(None)
      streamArn <-
        if (consumerArn.isEmpty) streamArnGen.map(x => Some(x))
        else Gen.const(None)
    } yield DeregisterStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  )

  implicit val describeLimitsResponseArb: Arbitrary[DescribeLimitsResponse] =
    Arbitrary(
      for {
        shardLimit <- Gen.choose(1, 50)
        openShardCount <- Gen.choose(0, shardLimit)
      } yield DescribeLimitsResponse(openShardCount, shardLimit)
    )

  implicit val describeStreamConsumerRequestArb
      : Arbitrary[DescribeStreamConsumerRequest] = Arbitrary(
    for {
      consumerArn <- Gen.option(consumerArnGen)
      consumerName <-
        if (consumerArn.isEmpty) consuemrNameGen.map(x => Some(x))
        else Gen.const(None)
      streamArn <-
        if (consumerArn.isEmpty) streamArnGen.map(x => Some(x))
        else Gen.const(None)
    } yield DescribeStreamConsumerRequest(consumerArn, consumerName, streamArn)
  )

  val keyIdGen: Gen[String] = Arbitrary.arbitrary[Boolean].flatMap {
    case true =>
      Arbitrary.arbitrary[Boolean].flatMap {
        case true => Gen.uuid.flatMap(key => arnGen("kms", "key", key.toString))
        case false =>
          for {
            arnPrefix <- arnPrefixGen("kms", "alias")
            aliasLen <- Gen.choose(1, 2048 - arnPrefix.length())
            alias <- Gen.stringOfN(aliasLen, Gen.alphaNumChar)
          } yield s"$arnPrefix$alias"
      }
    case false =>
      Arbitrary.arbitrary[Boolean].flatMap {
        case true =>
          Gen.choose(1, 2042).flatMap { size =>
            Gen.stringOfN(size, Gen.alphaNumChar).map(x => s"alias/$x")
          }
        case false => Gen.uuid.map(_.toString())
      }
  }

  implicit val streamDescriptionArb: Arbitrary[StreamDescription] = Arbitrary(
    for {
      encryptionType <- Gen.option(Arbitrary.arbitrary[EncryptionType])
      enhancedMonitoring <- Gen
        .choose(0, 1)
        .flatMap(size =>
          Gen.listOfN(size, shardLevelMetricsArbitrary.arbitrary)
        )
      hasMoreShards <- Arbitrary.arbitrary[Boolean]
      keyId <- Gen.option(keyIdGen)
      retentionPeriodHours <- retentionPeriodHoursGen
      shardCount <- Gen.choose(2, 50)
      shardGens = List
        .range(1, shardCount)
        .map(index => shardGen(index).map(ShardSummary.fromShard))
      shards <- Gen.sequence[List[ShardSummary], ShardSummary](shardGens)
      streamCreationTimestamp <- nowGen
      streamName <- streamNameGen
      streamArn <- arnGen("kinesis", "stream", streamName)
      streamStatus <- Arbitrary.arbitrary[StreamStatus]
    } yield StreamDescription(
      encryptionType,
      enhancedMonitoring,
      hasMoreShards,
      keyId,
      retentionPeriodHours,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamName,
      streamStatus
    )
  )

  val limitGen: Gen[Int] = Gen.choose(1, 10000)

  implicit val describeStreamRequestArb: Arbitrary[DescribeStreamRequest] =
    Arbitrary(for {
      exclusiveStartShardId <- Gen.option(
        Gen.choose(0, 1000).flatMap(index => Shard.shardId(index))
      )
      limit <- Gen.option(limitGen)
      streamName <- streamNameGen
    } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName))

  implicit val describeStreamResponseArb: Arbitrary[DescribeStreamResponse] =
    Arbitrary(
      streamDescriptionArb.arbitrary.map(DescribeStreamResponse.apply)
    )

}
