package kinesis.mock.instances

import scala.collection.SortedMap
import scala.collection.immutable.Queue
import scala.concurrent.duration._

import java.time.Instant

import enumeratum.scalacheck._
import org.scalacheck.{Arbitrary, Gen}
import wolfendale.scalacheck.regexp.RegexpGen

import kinesis.mock.api._
import kinesis.mock.models._

object arbitrary {

  val awsAccountIdGen: Gen[AwsAccountId] =
    Gen.stringOfN(12, Gen.numChar).map(AwsAccountId.apply)
  implicit val awsAccountIdArb: Arbitrary[AwsAccountId] = Arbitrary(
    awsAccountIdGen
  )

  def arnPrefixGen(service: String, part: String): Gen[String] = for {
    accountId <- awsAccountIdGen
    region <- Arbitrary.arbitrary[AwsRegion]
  } yield s"arn:${region.awsArnPiece}:$service:${region.entryName}:$accountId:$part/"

  def arnGen(service: String, part: String, value: String): Gen[String] =
    arnPrefixGen(service, part).map(arnPrefix => s"$arnPrefix$value")

  val streamNameGen: Gen[StreamName] =
    Gen
      .choose(1, 128)
      .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))
      .map(StreamName.apply)

  implicit val streamNameArbitrary: Arbitrary[StreamName] = Arbitrary(
    streamNameGen
  )

  val streamArnGen: Gen[StreamArn] = for {
    streamName <- streamNameGen
    awsAccountId <- awsAccountIdGen
    awsRegion <- Arbitrary.arbitrary[AwsRegion]
  } yield StreamArn(awsRegion, streamName, awsAccountId)

  implicit val streamArnArb: Arbitrary[StreamArn] = Arbitrary(streamArnGen)

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

  val consumerNameGen: Gen[ConsumerName] = Gen
    .choose(1, 128)
    .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))
    .map(ConsumerName.apply)

  implicit val consumerNameArb: Arbitrary[ConsumerName] = Arbitrary(
    consumerNameGen
  )

  val consumerArnGen: Gen[ConsumerArn] = for {
    streamArn <- streamArnGen
    consumerName <- consumerNameGen
    consumerCreationTimestamp <- nowGen
  } yield ConsumerArn(streamArn, consumerName, consumerCreationTimestamp)

  implicit val consumerArnArbitrary: Arbitrary[ConsumerArn] = Arbitrary(
    consumerArnGen
  )

  implicit val consumerArbitrary: Arbitrary[Consumer] = Arbitrary(
    for {
      streamArn <- streamArnGen
      consumerCreationTimestamp <- nowGen
      consumerName <- consumerNameGen
      consumerArn = ConsumerArn(
        streamArn,
        consumerName,
        consumerCreationTimestamp
      )
      consumerStatus <- Arbitrary.arbitrary[ConsumerStatus]
    } yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus,
      streamArn
    )
  )

  implicit val consumerSummaryArb: Arbitrary[ConsumerSummary] = Arbitrary(
    consumerArbitrary.arbitrary.map(ConsumerSummary.fromConsumer)
  )

  implicit val hashKeyRangeArbitrary: Arbitrary[HashKeyRange] = Arbitrary(
    for {
      startingHashKey <- Gen.posNum[Int].map(BigInt.apply)
      endingHashKey <- Gen.posNum[Int].map(i => BigInt(i) + startingHashKey)
    } yield HashKeyRange(startingHashKey, endingHashKey)
  )

  val dataGen: Gen[Array[Byte]] =
    Arbitrary.arbitrary[Array[Byte]].suchThat(_.length < 1048576)

  implicit val kinesisRecordArbitrary: Arbitrary[KinesisRecord] = Arbitrary(
    for {
      approximateArrivalTimestamp <- nowGen
      data <- dataGen
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
        .map(x => ShardLevelMetrics(x.toVector))
    )

  def shardGen(shardIndex: Int): Gen[Shard] = for {
    shard <- Gen.const(ShardId.create(shardIndex))
    createdAtTimestamp <- nowGen.map(_.minusSeconds(10000))
    adjacentParentShardId <- Gen.option(Gen.const(ShardId.create(0).shardId))
    parentShardId <- Gen.option(Gen.const(ShardId.create(1).shardId))
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
    shard
  )

  def shardSummaryGen(shardIndex: Int): Gen[ShardSummary] =
    shardGen(shardIndex).map(ShardSummary.fromShard)

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
        .resize(size, RegexpGen.from("^([a-zA-Z0-9_.:/=+\\-@ ]*)$"))
        .suchThat(x => !x.startsWith("aws:") && x.nonEmpty)
    )

  val tagValueGen: Gen[String] = Gen
    .choose(0, 256)
    .flatMap(size =>
      Gen.resize(size, RegexpGen.from("^([a-zA-Z0-9_.:/=+\\-@ ]*)$"))
    )

  val tagsGen: Gen[Tags] = Gen
    .choose(0, 10)
    .flatMap(size => Gen.mapOfN(size, Gen.zip(tagKeyGen, tagValueGen)))
    .map(x => SortedMap.from(x))
    .map(Tags.apply)

  implicit val tagListArb: Arbitrary[TagList] = Arbitrary(
    tagsGen.map(TagList.fromTags)
  )

  implicit val tagListEntryArb: Arbitrary[TagListEntry] = Arbitrary(
    for {
      key <- tagKeyGen
      value <- tagValueGen
    } yield TagListEntry(key, value)
  )

  implicit val tagsArbitrary: Arbitrary[Tags] = Arbitrary(tagsGen)

  implicit val addTagsToStreamRequestArbitrary
      : Arbitrary[AddTagsToStreamRequest] = Arbitrary(
    for {
      streamName <- streamNameGen
      tags <- tagsGen
    } yield AddTagsToStreamRequest(streamName, tags)
  )

  implicit val createStreamRequestArb: Arbitrary[CreateStreamRequest] = {
    Arbitrary(
      for {
        shardCount: Option[Int] <- Gen.option(Gen.choose(1, 1000))
        streamName <- streamNameGen
      } yield CreateStreamRequest(shardCount, streamName)
    )
  }

  val retentionPeriodHoursGen: Gen[Int] = Gen.choose(
    StreamData.minRetentionPeriod.toHours.toInt,
    StreamData.maxRetentionPeriod.toHours.toInt
  )

  implicit val decreaseStreamRetentionRequestArb
      : Arbitrary[DecreaseStreamRetentionPeriodRequest] = Arbitrary(
    for {
      retentionPeriodHours <- retentionPeriodHoursGen
      streamName <- streamNameGen
    } yield DecreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName
    )
  )

  implicit val deleteStreamRequestArb: Arbitrary[DeleteStreamRequest] =
    Arbitrary(
      for {
        streamName <- streamNameGen
        enforceConsumerDeletion <- Gen.option(Arbitrary.arbitrary[Boolean])
      } yield DeleteStreamRequest(streamName, enforceConsumerDeletion)
    )

  implicit val deregisterStreamConsumerRequestArb
      : Arbitrary[DeregisterStreamConsumerRequest] = Arbitrary(
    for {
      consumerArn <- Gen.option(consumerArnGen)
      consumerName <-
        if (consumerArn.isEmpty) consumerNameGen.map(x => Some(x))
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
        if (consumerArn.isEmpty) consumerNameGen.map(x => Some(x))
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
          Gen.containerOfN[Vector, ShardLevelMetrics](
            size,
            shardLevelMetricsArbitrary.arbitrary
          )
        )
      hasMoreShards <- Arbitrary.arbitrary[Boolean]
      keyId <- Gen.option(keyIdGen)
      retentionPeriodHours <- retentionPeriodHoursGen
      shardCount <- Gen.choose(2, 50)
      shardGens = List
        .range(1, shardCount)
        .map(index => shardGen(index).map(ShardSummary.fromShard))
      shards <- Gen.sequence[Vector[ShardSummary], ShardSummary](shardGens)
      streamCreationTimestamp <- nowGen
      streamName <- streamNameGen
      awsRegion <- Arbitrary.arbitrary[AwsRegion]
      awsAccountId <- awsAccountIdGen
      streamArn = StreamArn(awsRegion, streamName, awsAccountId)
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

  implicit val shardIdArbitrary: Arbitrary[ShardId] = Arbitrary(
    Gen.choose(0, 1000).map(index => ShardId.create(index))
  )

  implicit val describeStreamRequestArb: Arbitrary[DescribeStreamRequest] =
    Arbitrary(for {
      exclusiveStartShardId <- Gen.option(
        shardIdArbitrary.arbitrary.map(_.shardId)
      )
      limit <- Gen.option(limitGen)
      streamName <- streamNameGen
    } yield DescribeStreamRequest(exclusiveStartShardId, limit, streamName))

  implicit val describeStreamResponseArb: Arbitrary[DescribeStreamResponse] =
    Arbitrary(
      streamDescriptionArb.arbitrary.map(DescribeStreamResponse.apply)
    )

  implicit val describeStreamSummaryRequestArb
      : Arbitrary[DescribeStreamSummaryRequest] = Arbitrary(
    streamNameGen.map(DescribeStreamSummaryRequest.apply)
  )

  implicit val streamDescriptionSummaryArb
      : Arbitrary[StreamDescriptionSummary] = Arbitrary(
    for {
      consumerCount <- Gen.option(Gen.choose(1, 20))
      encryptionType <- Gen.option(Arbitrary.arbitrary[EncryptionType])
      enhancedMonitoring <- Gen
        .choose(0, 1)
        .flatMap(size =>
          Gen.containerOfN[Vector, ShardLevelMetrics](
            size,
            shardLevelMetricsArbitrary.arbitrary
          )
        )
      keyId <- Gen.option(keyIdGen)
      openShardCount <- Gen.choose(1, 50)
      retentionPeriodHours <- retentionPeriodHoursGen
      streamCreationTimestamp <- nowGen
      streamName <- streamNameGen
      awsRegion <- Arbitrary.arbitrary[AwsRegion]
      awsAccountId <- awsAccountIdGen
      streamArn = StreamArn(awsRegion, streamName, awsAccountId)
      streamStatus <- Arbitrary.arbitrary[StreamStatus]
    } yield StreamDescriptionSummary(
      consumerCount,
      encryptionType,
      enhancedMonitoring,
      keyId,
      openShardCount,
      retentionPeriodHours,
      streamArn,
      streamCreationTimestamp,
      streamName,
      streamStatus
    )
  )

  implicit val describeStreamSummaryResponseArb
      : Arbitrary[DescribeStreamSummaryResponse] = Arbitrary(
    streamDescriptionSummaryArb.arbitrary.map(
      DescribeStreamSummaryResponse.apply
    )
  )

  implicit val disableEnhancedMonitoringRequestArb
      : Arbitrary[DisableEnhancedMonitoringRequest] = Arbitrary(
    for {
      shardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamName <- streamNameGen
    } yield DisableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  )

  implicit val disableEnhancedMonitoringResponseArb
      : Arbitrary[DisableEnhancedMonitoringResponse] = Arbitrary(
    for {
      currentShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      desiredShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamName <- streamNameGen
    } yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  )

  implicit val enableEnhancedMonitoringRequestArb
      : Arbitrary[EnableEnhancedMonitoringRequest] = Arbitrary(
    for {
      shardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamName <- streamNameGen
    } yield EnableEnhancedMonitoringRequest(shardLevelMetrics, streamName)
  )

  implicit val enableEnhancedMonitoringResponseArb
      : Arbitrary[EnableEnhancedMonitoringResponse] = Arbitrary(
    for {
      currentShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      desiredShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamName <- streamNameGen
    } yield EnableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  )

  val shardIteratorGen: Gen[ShardIterator] = for {
    streamName <- streamNameGen
    shardId <- shardIdArbitrary.arbitrary
    sequenceNumber <- sequenceNumberArbitrary.arbitrary
  } yield ShardIterator.create(streamName, shardId.shardId, sequenceNumber)

  implicit val getRecordsRequestArb: Arbitrary[GetRecordsRequest] = Arbitrary(
    for {
      limit <- Gen.option(limitGen)
      shardIterator <- shardIteratorGen
    } yield GetRecordsRequest(limit, shardIterator)
  )

  val childShardGen: Gen[ChildShard] = for {
    shardIndex <- Gen.choose(100, 1000)
    shardId = ShardId.create(shardIndex).shardId
    parentShards = Vector
      .range(0, shardIndex)
      .map(ShardId.create)
      .map(_.shardId)
    hashKeyRange <- hashKeyRangeArbitrary.arbitrary
  } yield ChildShard(hashKeyRange, parentShards, shardId)

  implicit val getRecordsResponseArb: Arbitrary[GetRecordsResponse] = Arbitrary(
    for {
      childShards <- Gen.containerOf[Vector, ChildShard](childShardGen)
      millisBehindLatest <- Gen.choose(0L, 1.day.toMillis)
      nextShardIterator <- shardIteratorGen
      records <- Gen
        .choose(0, 100)
        .flatMap(size =>
          Gen.containerOfN[Queue, KinesisRecord](
            size,
            kinesisRecordArbitrary.arbitrary
          )
        )
    } yield GetRecordsResponse(
      childShards,
      millisBehindLatest,
      nextShardIterator,
      records
    )
  )

  implicit val getShardIteratorRequestArb: Arbitrary[GetShardIteratorRequest] =
    Arbitrary(
      for {
        shardId <- shardIdArbitrary.arbitrary
        shardIteratorType <- Arbitrary.arbitrary[ShardIteratorType]
        startingSequenceNumber <- shardIteratorType match {
          case ShardIteratorType.AFTER_SEQUENCE_NUMBER |
              ShardIteratorType.AT_SEQUENCE_NUMBER =>
            sequenceNumberArbitrary.arbitrary.map(x => Some(x))
          case _ => Gen.const(None)
        }
        streamName <- streamNameGen
        timestamp <- shardIteratorType match {
          case ShardIteratorType.AT_TIMESTAMP => nowGen.map(x => Some(x))
          case _                              => Gen.const(None)
        }
      } yield GetShardIteratorRequest(
        shardId.shardId,
        shardIteratorType,
        startingSequenceNumber,
        streamName,
        timestamp
      )
    )

  implicit val getShardIteratorResponseArb
      : Arbitrary[GetShardIteratorResponse] = Arbitrary(
    shardIteratorGen.map(GetShardIteratorResponse.apply)
  )

  implicit val increaseStreamRetentionRequestArb
      : Arbitrary[IncreaseStreamRetentionPeriodRequest] = Arbitrary(
    for {
      retentionPeriodHours <- retentionPeriodHoursGen
      streamName <- streamNameGen
    } yield IncreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName
    )
  )

  def nextTokenGen(exclusiveStartShardIndex: Option[Int]): Gen[String] = for {
    streamName <- streamNameGen
    lastShardId <-
      Gen
        .choose(exclusiveStartShardIndex.getOrElse(0), 1000)
        .map(ShardId.create)
  } yield ListShardsRequest.createNextToken(streamName, lastShardId.shardId)

  def shardFilterGen(exclusiveStartShardIndex: Option[Int]): Gen[ShardFilter] =
    for {
      shardFilterType <- Arbitrary.arbitrary[ShardFilterType]
      shardFilterShardId <- shardFilterType match {
        case ShardFilterType.AFTER_SHARD_ID =>
          Gen
            .choose(exclusiveStartShardIndex.getOrElse(0), 1000)
            .map(ShardId.create)
            .map(x => Some(x.shardId))
        case _ => Gen.const(None)
      }
      shardFilterTimestamp <- shardFilterType match {
        case ShardFilterType.AT_TIMESTAMP => nowGen.map(x => Some(x))
        case _                            => Gen.const(None)
      }
      shardFilter <-
        Gen.const(
          ShardFilter(shardFilterShardId, shardFilterTimestamp, shardFilterType)
        )
    } yield shardFilter

  implicit val shardFilterArbitrary: Arbitrary[ShardFilter] = Arbitrary(
    shardFilterGen(None)
  )

  implicit val listShardsRequestArb: Arbitrary[ListShardsRequest] = Arbitrary(
    for {
      exclusiveStartShardId <- Gen.option(shardIdArbitrary.arbitrary)
      maxResults <- Gen.option(Gen.choose(1, 10000))
      nextToken <- Gen.option(nextTokenGen(exclusiveStartShardId.map(_.index)))
      shardFilter <- Gen.option(
        shardFilterGen(exclusiveStartShardId.map(_.index))
      )
      streamCreationTimestamp <- Gen.option(nowGen)
      streamName <- Gen.option(streamNameGen)
    } yield ListShardsRequest(
      exclusiveStartShardId.map(_.shardId),
      maxResults,
      nextToken,
      shardFilter,
      streamCreationTimestamp,
      streamName
    )
  )

  implicit val listShardsResponseArb: Arbitrary[ListShardsResponse] = Arbitrary(
    for {
      nextToken <- Gen.option(nextTokenGen(None))
      shards <- Gen.sequence[Vector[ShardSummary], ShardSummary](
        Vector.range(0, 100).map(x => shardSummaryGen(x))
      )
    } yield ListShardsResponse(nextToken, shards)
  )

  implicit val listStreamConsumersRequestArb
      : Arbitrary[ListStreamConsumersRequest] = Arbitrary(
    for {
      maxResults <- Gen.option(limitGen)
      nextToken <- Gen.option(consumerNameGen)
      streamArn <- streamArnGen
      streamCreationTimestamp <- Gen.option(nowGen)
    } yield ListStreamConsumersRequest(
      maxResults,
      nextToken,
      streamArn,
      streamCreationTimestamp
    )
  )

  implicit val listStreamConsumersResponseArb
      : Arbitrary[ListStreamConsumersResponse] = Arbitrary(
    for {
      size <- Gen.choose(0, 20)
      consumers <- Gen.containerOfN[Vector, ConsumerSummary](
        size,
        consumerSummaryArb.arbitrary
      )
      nextToken = consumers.lastOption.map(_.consumerName)
    } yield ListStreamConsumersResponse(consumers, nextToken)
  )

  implicit val listStreamsRequestArb: Arbitrary[ListStreamsRequest] = Arbitrary(
    for {
      exclusiveStartStreamName <- Gen.option(streamNameGen)
      limit <- Gen.option(limitGen)
    } yield ListStreamsRequest(exclusiveStartStreamName, limit)
  )

  implicit val listStreamsResponseArb: Arbitrary[ListStreamsResponse] =
    Arbitrary(
      for {
        hasMoreStreams <- Arbitrary.arbitrary[Boolean]
        size <- Gen.choose(0, 50)
        streamNames <- Gen.containerOfN[Vector, StreamName](size, streamNameGen)
      } yield ListStreamsResponse(hasMoreStreams, streamNames)
    )

  implicit val listTagsForStreamRequestArb
      : Arbitrary[ListTagsForStreamRequest] = Arbitrary(
    for {
      exclusiveStartTagKey <- Gen.option(tagKeyGen)
      limit <- Gen.option(limitGen)
      streamName <- streamNameGen
    } yield ListTagsForStreamRequest(exclusiveStartTagKey, limit, streamName)
  )

  implicit val listTagsForStreamResponseArb
      : Arbitrary[ListTagsForStreamResponse] = Arbitrary(
    for {
      hasMoreTags <- Arbitrary.arbitrary[Boolean]
      tags <- tagListArb.arbitrary
    } yield ListTagsForStreamResponse(hasMoreTags, tags)
  )

  implicit val mergeShardsRequestArb: Arbitrary[MergeShardsRequest] = Arbitrary(
    for {
      adjacentShardToMerge <- shardIdArbitrary.arbitrary
      shardToMerge <- shardIdArbitrary.arbitrary.suchThat(
        _ != adjacentShardToMerge
      )
      streamName <- streamNameGen
    } yield MergeShardsRequest(
      adjacentShardToMerge.shardId,
      shardToMerge.shardId,
      streamName
    )
  )

  val explicitHashKeyGen: Gen[String] =
    Gen.choose(Shard.minHashKey, Shard.maxHashKey).map(_.toString)
  val partitionKeyGen: Gen[String] =
    Gen.choose(1, 256).flatMap(size => Gen.stringOfN(size, Gen.alphaNumChar))

  implicit val putRecordRequestArb: Arbitrary[PutRecordRequest] = Arbitrary(
    for {
      data <- dataGen
      explicitHashKey <- Gen.option(explicitHashKeyGen)
      partitionKey <- partitionKeyGen
      sequenceNumberForOrdering <- Gen.option(sequenceNumberArbitrary.arbitrary)
      streamName <- streamNameGen
    } yield PutRecordRequest(
      data,
      explicitHashKey,
      partitionKey,
      sequenceNumberForOrdering,
      streamName
    )
  )

  implicit val putRecordResponseArb: Arbitrary[PutRecordResponse] = Arbitrary(
    for {
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      sequenceNumber <- sequenceNumberArbitrary.arbitrary
      shardId <- shardIdArbitrary.arbitrary
    } yield PutRecordResponse(encryptionType, sequenceNumber, shardId.shardId)
  )

  implicit val putRecordsRequestEntryArb: Arbitrary[PutRecordsRequestEntry] =
    Arbitrary(
      for {
        data <- dataGen
        explicitHashKey <- Gen.option(explicitHashKeyGen)
        partitionKey <- partitionKeyGen
      } yield PutRecordsRequestEntry(data, explicitHashKey, partitionKey)
    )

  implicit val putRecordsRequestArb: Arbitrary[PutRecordsRequest] = Arbitrary(
    for {
      recordsSize <- Gen.choose(0, 500)
      records <- Gen.containerOfN[Vector, PutRecordsRequestEntry](
        recordsSize,
        putRecordsRequestEntryArb.arbitrary
      )
      streamName <- streamNameGen
    } yield PutRecordsRequest(records, streamName)
  )

  implicit val putRecordsResultEntry: Arbitrary[PutRecordsResultEntry] =
    Arbitrary(
      for {
        errorCode <- Gen.option(Arbitrary.arbitrary[PutRecordsErrorCode])
        errorMessage <- errorCode match {
          case Some(_) => Gen.stringOfN(256, Gen.alphaNumChar).map(Some(_))
          case None    => Gen.const(None)
        }
        sequenceNumber <- errorCode match {
          case Some(_) => Gen.const(None)
          case None    => sequenceNumberArbitrary.arbitrary.map(Some(_))
        }
        shardId <- errorCode match {
          case Some(_) => Gen.const(None)
          case None    => Gen.option(shardIdArbitrary.arbitrary.map(_.shardId))
        }
      } yield PutRecordsResultEntry(
        errorCode,
        errorMessage,
        sequenceNumber,
        shardId
      )
    )

  implicit val putRecordsResponseArb: Arbitrary[PutRecordsResponse] = Arbitrary(
    for {
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      failedRecordCount <- Gen.choose(0, 500)
      recordsSize <- Gen.choose(failedRecordCount, 500)
      records <- Gen.containerOfN[Vector, PutRecordsResultEntry](
        recordsSize,
        putRecordsResultEntry.arbitrary
      )
    } yield PutRecordsResponse(encryptionType, failedRecordCount, records)
  )

  implicit val registerStreamConsumerRequestArb
      : Arbitrary[RegisterStreamConsumerRequest] = Arbitrary(
    for {
      consumerName <- consumerNameGen
      streamArn <- streamArnGen
    } yield RegisterStreamConsumerRequest(consumerName, streamArn)
  )

  implicit val registerStreamConsumerResponseArb
      : Arbitrary[RegisterStreamConsumerResponse] = Arbitrary(
    consumerSummaryArb.arbitrary.map(RegisterStreamConsumerResponse.apply)
  )

  implicit val shardIteratorArbitrary: Arbitrary[ShardIterator] = Arbitrary(
    shardIteratorGen
  )

  implicit val splitShardRequestArb: Arbitrary[SplitShardRequest] = Arbitrary(
    for {
      newStartingHashKey <- hashKeyRangeArbitrary.arbitrary.map(
        _.startingHashKey.toString
      )
      shardToSplit <- shardIdArbitrary.arbitrary.map(_.shardId)
      streamName <- streamNameGen
    } yield SplitShardRequest(newStartingHashKey, shardToSplit, streamName)
  )

  implicit val startStreamEncryptionRequestArb
      : Arbitrary[StartStreamEncryptionRequest] = Arbitrary(
    for {
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      keyId <- keyIdGen
      streamName <- streamNameGen
    } yield StartStreamEncryptionRequest(encryptionType, keyId, streamName)
  )

  implicit val stopStreamEncryptionRequestArb
      : Arbitrary[StopStreamEncryptionRequest] = Arbitrary(
    for {
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      keyId <- keyIdGen
      streamName <- streamNameGen
    } yield StopStreamEncryptionRequest(encryptionType, keyId, streamName)
  )

  implicit val updateShardCountRequestArb: Arbitrary[UpdateShardCountRequest] =
    Arbitrary(
      for {
        scalingType <- Arbitrary.arbitrary[ScalingType]
        streamName <- streamNameGen
        targetShardCount <- Gen.choose(1, 1000)
      } yield UpdateShardCountRequest(scalingType, streamName, targetShardCount)
    )

  implicit val updateShardCountResponseArb
      : Arbitrary[UpdateShardCountResponse] =
    Arbitrary(
      for {
        streamName <- streamNameGen
        targetShardCount <- Gen.choose(1, 1000)
        currentShardCount <- Gen.choose(1, 1000)
      } yield UpdateShardCountResponse(
        currentShardCount,
        streamName,
        targetShardCount
      )
    )

  implicit val streamDataArbitrary: Arbitrary[StreamData] = Arbitrary(
    for {
      consumersSize <- Gen.choose(0, 20)
      consumers <- Gen
        .listOfN(
          consumersSize,
          consumerArbitrary.arbitrary.map(x => x.consumerName -> x)
        )
        .map(x => SortedMap.from(x))
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      enhancedMonitoring <- Gen
        .choose(0, 1)
        .flatMap(size =>
          Gen.containerOfN[Vector, ShardLevelMetrics](
            size,
            shardLevelMetricsArbitrary.arbitrary
          )
        )
      keyId <- Gen.option(keyIdGen)
      retentionPeriod <- retentionPeriodHoursGen.map(_.hours)
      shardsSize <- Gen.choose(0, 50)
      shardList <- Gen
        .containerOfN[Vector, Shard](shardsSize, shardArbitrary.arbitrary)
      shards <- Gen.sequence[SortedMap[
        Shard,
        Vector[KinesisRecord]
      ], (Shard, Vector[KinesisRecord])](
        shardList.map(shard =>
          Gen
            .choose(0, 100)
            .flatMap(recordsSize =>
              Gen
                .containerOfN[Vector, KinesisRecord](
                  recordsSize,
                  kinesisRecordArbitrary.arbitrary
                )
                .map(records => shard -> records)
            )
        )
      )
      streamName <- streamNameGen
      awsRegion <- Arbitrary.arbitrary[AwsRegion]
      awsAccountId <- awsAccountIdGen
      streamArn = StreamArn(awsRegion, streamName, awsAccountId)
      streamCreationTimestamp <- nowGen
      streamStatus <- Arbitrary.arbitrary[StreamStatus]
      tags <- tagsGen
      shardCountUpdates <- Gen
        .choose(0, 10)
        .flatMap(size => Gen.containerOfN[Vector, Instant](size, nowGen))
    } yield StreamData(
      consumers,
      encryptionType,
      enhancedMonitoring,
      keyId,
      retentionPeriod,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamName,
      streamStatus,
      tags,
      shardCountUpdates
    )
  )

  implicit val streamsArb: Arbitrary[Streams] = Arbitrary {
    Gen
      .choose(0, 2)
      .flatMap(size => Gen.listOfN(size, streamDataArbitrary.arbitrary))
      .suchThat(x =>
        x.groupBy(_.streamArn).filter { case (_, x) => x.length > 1 }.isEmpty
      )
      .map(x => Streams(SortedMap.from(x.map(sd => sd.streamArn -> sd))))
  }

}
