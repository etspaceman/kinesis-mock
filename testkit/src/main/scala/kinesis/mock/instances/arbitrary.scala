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
package instances

import scala.collection.SortedMap
import scala.collection.immutable.Queue
import scala.concurrent.duration.*

import java.time.Instant

import enumeratum.scalacheck.*
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.api.*
import kinesis.mock.models.*
import kinesis.mock.regexp.RegexpGen

object arbitrary:

  val awsAccountIdGen: Gen[AwsAccountId] =
    Gen.stringOfN(12, Gen.numChar).map(AwsAccountId.apply)
  given awsAccountIdArb: Arbitrary[AwsAccountId] = Arbitrary(
    awsAccountIdGen
  )
  def arnPrefixGen(service: String, part: String): Gen[String] = for
    accountId <- awsAccountIdGen
    region <- Arbitrary.arbitrary[AwsRegion]
  yield s"arn:${region.awsArnPiece}:$service:${region.entryName}:$accountId:$part/"

  def arnGen(service: String, part: String, value: String): Gen[String] =
    arnPrefixGen(service, part).map(arnPrefix => s"$arnPrefix$value")

  val streamNameGen: Gen[StreamName] =
    Gen
      .choose(1, 128)
      .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))
      .map(StreamName.apply)

  given streamNameArbitrary: Arbitrary[StreamName] = Arbitrary(
    streamNameGen
  )

  val streamArnGen: Gen[StreamArn] = for
    streamName <- streamNameGen
    awsAccountId <- awsAccountIdGen
    awsRegion <- Arbitrary.arbitrary[AwsRegion]
  yield StreamArn(awsRegion, streamName, awsAccountId)

  given streamArnArb: Arbitrary[StreamArn] = Arbitrary(streamArnGen)

  val streamNameOrArnGen: Gen[(Option[StreamName], Option[StreamArn])] = for
    streamName <- Gen.option(streamNameGen)
    streamArn <-
      if streamName.isEmpty then streamArnGen.map(Some(_)) else Gen.const(None)
  yield (streamName, streamArn)

  val nowGen: Gen[Instant] = Gen.delay(Gen.const(Instant.now))

  given sequenceNumberArbitrary: Arbitrary[SequenceNumber] = Arbitrary(
    Gen.option(Arbitrary.arbitrary[SequenceNumberConstant]).flatMap {
      case Some(constant) => SequenceNumber(constant.entryName)
      case None           =>
        for
          shardCreateTime <- nowGen.map(_.minusSeconds(300))
          shardIndex <- Gen.posNum[Int]
          seqIndex <- Gen.option(Gen.posNum[Int])
          seqTime <- Gen.option(nowGen.map(_.minusSeconds(5)))
        yield SequenceNumber
          .create(shardCreateTime, shardIndex, None, seqIndex, seqTime)
    }
  )

  val consumerNameGen: Gen[ConsumerName] = Gen
    .choose(1, 128)
    .flatMap(size => Gen.resize(size, RegexpGen.from("[a-zA-Z0-9_.-]+")))
    .map(ConsumerName.apply)

  given consumerNameArb: Arbitrary[ConsumerName] = Arbitrary(
    consumerNameGen
  )

  val consumerArnGen: Gen[ConsumerArn] = for
    streamArn <- streamArnGen
    consumerName <- consumerNameGen
    consumerCreationTimestamp <- nowGen
  yield ConsumerArn(streamArn, consumerName, consumerCreationTimestamp)

  given consumerArnArbitrary: Arbitrary[ConsumerArn] = Arbitrary(
    consumerArnGen
  )

  given consumerArbitrary: Arbitrary[Consumer] = Arbitrary(
    for
      streamArn <- streamArnGen
      consumerCreationTimestamp <- nowGen
      consumerName <- consumerNameGen
      consumerArn = ConsumerArn(
        streamArn,
        consumerName,
        consumerCreationTimestamp
      )
      consumerStatus <- Arbitrary.arbitrary[ConsumerStatus]
    yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus,
      streamArn
    )
  )

  given consumerSummaryArb: Arbitrary[ConsumerSummary] = Arbitrary(
    consumerArbitrary.arbitrary.map(ConsumerSummary.fromConsumer)
  )

  given hashKeyRangeArbitrary: Arbitrary[HashKeyRange] = Arbitrary(
    for
      startingHashKey <- Gen.posNum[Int].map(BigInt.apply)
      endingHashKey <- Gen.posNum[Int].map(i => BigInt(i) + startingHashKey)
    yield HashKeyRange(startingHashKey, endingHashKey)
  )

  val dataGen: Gen[Array[Byte]] =
    Arbitrary.arbitrary[Array[Byte]].suchThat(_.length < 1048576)

  given kinesisRecordArbitrary: Arbitrary[KinesisRecord] = Arbitrary(
    for
      approximateArrivalTimestamp <- nowGen
      data <- dataGen
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      partitionKey <- Gen
        .choose(1, 256)
        .flatMap(size => Gen.stringOfN(size, Gen.alphaNumChar))
      sequenceNumber <- sequenceNumberArbitrary.arbitrary
    yield KinesisRecord(
      approximateArrivalTimestamp,
      data,
      encryptionType,
      partitionKey,
      sequenceNumber
    )
  )

  given sequenceNumberRangeArbitrary: Arbitrary[SequenceNumberRange] =
    Arbitrary(
      for
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
      yield SequenceNumberRange(endingSequenceNumber, startingSequenceNumber)
    )

  given shardLevelMetricsArbitrary: Arbitrary[ShardLevelMetrics] =
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

  def shardGen(shardIndex: Int): Gen[Shard] = for
    shard <- Gen.const(ShardId.create(shardIndex))
    createdAtTimestamp <- nowGen.map(_.minusSeconds(10000))
    adjacentParentShardId <- Gen.option(Gen.const(ShardId.create(0).shardId))
    parentShardId <- Gen.option(Gen.const(ShardId.create(1).shardId))
    hashKeyRange <- hashKeyRangeArbitrary.arbitrary
    sequenceNumberRange <- sequenceNumberRangeArbitrary.arbitrary
    closedTimestamp <- Gen
      .option(nowGen)
      .map(ts => sequenceNumberRange.endingSequenceNumber.flatMap(_ => ts))
  yield Shard(
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

  given shardArbitrary: Arbitrary[Shard] = Arbitrary(
    Gen.choose(100, 1000).flatMap(index => shardGen(index))
  )

  given shardSummaryArbitrary: Arbitrary[ShardSummary] = Arbitrary(
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

  given tagListArb: Arbitrary[TagList] = Arbitrary(
    tagsGen.map(TagList.fromTags)
  )

  given tagListEntryArb: Arbitrary[TagListEntry] = Arbitrary(
    for
      key <- tagKeyGen
      value <- tagValueGen
    yield TagListEntry(key, value)
  )

  given tagsArbitrary: Arbitrary[Tags] = Arbitrary(tagsGen)

  given addTagsToStreamRequestArbitrary: Arbitrary[AddTagsToStreamRequest] =
    Arbitrary(
      for
        (streamName, streamArn) <- streamNameOrArnGen
        tags <- tagsGen
      yield AddTagsToStreamRequest(streamName, streamArn, tags)
    )

  given streamModeDetailsArb: Arbitrary[StreamModeDetails] = Arbitrary(
    Arbitrary.arbitrary[StreamMode].map(StreamModeDetails.apply)
  )

  given createStreamRequestArb: Arbitrary[CreateStreamRequest] =
    Arbitrary(
      for
        shardCount <- Gen.option(Gen.choose(1, 1000))
        streamName <- streamNameGen
        streamModeDetails <- Gen.option(streamModeDetailsArb.arbitrary)
      yield CreateStreamRequest(shardCount, streamModeDetails, streamName)
    )

  val retentionPeriodHoursGen: Gen[Int] = Gen.choose(
    StreamData.minRetentionPeriod.toHours.toInt,
    StreamData.maxRetentionPeriod.toHours.toInt
  )

  given decreaseStreamRetentionRequestArb
      : Arbitrary[DecreaseStreamRetentionPeriodRequest] = Arbitrary(
    for
      retentionPeriodHours <- retentionPeriodHoursGen
      (streamName, streamArn) <- streamNameOrArnGen
    yield DecreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName,
      streamArn
    )
  )

  given deleteStreamRequestArb: Arbitrary[DeleteStreamRequest] =
    Arbitrary(
      for
        (streamName, streamArn) <- streamNameOrArnGen
        enforceConsumerDeletion <- Gen.option(Arbitrary.arbitrary[Boolean])
      yield DeleteStreamRequest(
        streamName,
        streamArn,
        enforceConsumerDeletion
      )
    )

  given deregisterStreamConsumerRequestArb
      : Arbitrary[DeregisterStreamConsumerRequest] = Arbitrary(
    for
      consumerArn <- Gen.option(consumerArnGen)
      consumerName <-
        if consumerArn.isEmpty then consumerNameGen.map(x => Some(x))
        else Gen.const(None)
      streamArn <-
        if consumerArn.isEmpty then streamArnGen.map(x => Some(x))
        else Gen.const(None)
    yield DeregisterStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  )

  given describeLimitsResponseArb: Arbitrary[DescribeLimitsResponse] =
    Arbitrary(
      for
        onDemandStreamCountLimit <- Gen.choose(1, 10)
        onDemandStreamCount <- Gen.choose(0, onDemandStreamCountLimit)
        shardLimit <- Gen.choose(1, 50)
        openShardCount <- Gen.choose(0, shardLimit)
      yield DescribeLimitsResponse(
        onDemandStreamCount,
        onDemandStreamCountLimit,
        openShardCount,
        shardLimit
      )
    )

  given describeStreamConsumerRequestArb
      : Arbitrary[DescribeStreamConsumerRequest] = Arbitrary(
    for
      consumerArn <- Gen.option(consumerArnGen)
      consumerName <-
        if consumerArn.isEmpty then consumerNameGen.map(x => Some(x))
        else Gen.const(None)
      streamArn <-
        if consumerArn.isEmpty then streamArnGen.map(x => Some(x))
        else Gen.const(None)
    yield DescribeStreamConsumerRequest(consumerArn, consumerName, streamArn)
  )

  val keyIdGen: Gen[String] = Arbitrary.arbitrary[Boolean].flatMap {
    case true =>
      Arbitrary.arbitrary[Boolean].flatMap {
        case true => Gen.uuid.flatMap(key => arnGen("kms", "key", key.toString))
        case false =>
          for
            arnPrefix <- arnPrefixGen("kms", "alias")
            aliasLen <- Gen.choose(1, 2048 - arnPrefix.length())
            alias <- Gen.stringOfN(aliasLen, Gen.alphaNumChar)
          yield s"$arnPrefix$alias"
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

  given streamDescriptionArb: Arbitrary[StreamDescription] = Arbitrary(
    for
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
      streamModeDetails <- streamModeDetailsArb.arbitrary
      streamArn = StreamArn(awsRegion, streamName, awsAccountId)
      streamStatus <- Arbitrary.arbitrary[StreamStatus]
    yield StreamDescription(
      encryptionType,
      enhancedMonitoring,
      hasMoreShards,
      keyId,
      retentionPeriodHours,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamModeDetails,
      streamName,
      streamStatus
    )
  )

  val limitGen: Gen[Int] = Gen.choose(1, 10000)

  given shardIdArbitrary: Arbitrary[ShardId] = Arbitrary(
    Gen.choose(0, 1000).map(index => ShardId.create(index))
  )

  given describeStreamRequestArb: Arbitrary[DescribeStreamRequest] =
    Arbitrary(
      for
        exclusiveStartShardId <- Gen.option(
          shardIdArbitrary.arbitrary.map(_.shardId)
        )
        limit <- Gen.option(limitGen)
        (streamName, streamArn) <- streamNameOrArnGen
      yield DescribeStreamRequest(
        exclusiveStartShardId,
        limit,
        streamName,
        streamArn
      )
    )

  given describeStreamResponseArb: Arbitrary[DescribeStreamResponse] =
    Arbitrary(
      streamDescriptionArb.arbitrary.map(DescribeStreamResponse.apply)
    )

  given describeStreamSummaryRequestArb
      : Arbitrary[DescribeStreamSummaryRequest] = Arbitrary(
    streamNameOrArnGen.map { case (streamName, streamArn) =>
      DescribeStreamSummaryRequest(streamName, streamArn)
    }
  )

  given streamDescriptionSummaryArb: Arbitrary[StreamDescriptionSummary] =
    Arbitrary(
      for
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
        streamModeDetails <- streamModeDetailsArb.arbitrary
        streamName <- streamNameGen
        awsRegion <- Arbitrary.arbitrary[AwsRegion]
        awsAccountId <- awsAccountIdGen
        streamArn = StreamArn(awsRegion, streamName, awsAccountId)
        streamStatus <- Arbitrary.arbitrary[StreamStatus]
      yield StreamDescriptionSummary(
        consumerCount,
        encryptionType,
        enhancedMonitoring,
        keyId,
        openShardCount,
        retentionPeriodHours,
        streamArn,
        streamCreationTimestamp,
        streamModeDetails,
        streamName,
        streamStatus
      )
    )

  given describeStreamSummaryResponseArb
      : Arbitrary[DescribeStreamSummaryResponse] = Arbitrary(
    streamDescriptionSummaryArb.arbitrary.map(
      DescribeStreamSummaryResponse.apply
    )
  )

  given disableEnhancedMonitoringRequestArb
      : Arbitrary[DisableEnhancedMonitoringRequest] = Arbitrary(
    for
      shardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      (streamName, streamArn) <- streamNameOrArnGen
    yield DisableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  )

  given disableEnhancedMonitoringResponseArb
      : Arbitrary[DisableEnhancedMonitoringResponse] = Arbitrary(
    for
      currentShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      desiredShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamArn <- streamArnGen
    yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamArn.streamName,
      streamArn
    )
  )

  given enableEnhancedMonitoringRequestArb
      : Arbitrary[EnableEnhancedMonitoringRequest] = Arbitrary(
    for
      shardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      (streamName, streamArn) <- streamNameOrArnGen
    yield EnableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  )

  given enableEnhancedMonitoringResponseArb
      : Arbitrary[EnableEnhancedMonitoringResponse] = Arbitrary(
    for
      currentShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      desiredShardLevelMetrics <- shardLevelMetricsArbitrary.arbitrary.map(
        _.shardLevelMetrics
      )
      streamArn <- streamArnGen
    yield EnableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamArn.streamName,
      streamArn
    )
  )

  val shardIteratorGen: Gen[ShardIterator] = for
    streamName <- streamNameGen
    shardId <- shardIdArbitrary.arbitrary
    sequenceNumber <- sequenceNumberArbitrary.arbitrary
    now <- nowGen
  yield ShardIterator.create(streamName, shardId.shardId, sequenceNumber, now)

  given getRecordsRequestArb: Arbitrary[GetRecordsRequest] = Arbitrary(
    for
      limit <- Gen.option(limitGen)
      shardIterator <- shardIteratorGen
      now <- nowGen
      streamArn <- Gen.option(
        streamArnGen.map(x =>
          x.copy(streamName =
            shardIterator.parse(now).map(_.streamName).getOrElse(x.streamName)
          )
        )
      )
    yield GetRecordsRequest(limit, shardIterator, streamArn)
  )

  val childShardGen: Gen[ChildShard] = for
    shardIndex <- Gen.choose(100, 1000)
    shardId = ShardId.create(shardIndex).shardId
    parentShards = Vector
      .range(0, shardIndex)
      .map(ShardId.create)
      .map(_.shardId)
    hashKeyRange <- hashKeyRangeArbitrary.arbitrary
  yield ChildShard(hashKeyRange, parentShards, shardId)

  given getRecordsResponseArb: Arbitrary[GetRecordsResponse] = Arbitrary(
    for
      childShards <- Gen
        .containerOf[Vector, ChildShard](childShardGen)
        .flatMap {
          case x if x.nonEmpty => Gen.some(x)
          case _               => Gen.const(None)
        }
      millisBehindLatest <- Gen.choose(0L, 1.day.toMillis)
      nextShardIterator <-
        if childShards.nonEmpty then Gen.const(None)
        else Gen.some(shardIteratorGen)
      records <- Gen
        .choose(0, 100)
        .flatMap(size =>
          Gen.containerOfN[Queue, KinesisRecord](
            size,
            kinesisRecordArbitrary.arbitrary
          )
        )
    yield GetRecordsResponse(
      childShards,
      millisBehindLatest,
      nextShardIterator,
      records
    )
  )

  given getShardIteratorRequestArb: Arbitrary[GetShardIteratorRequest] =
    Arbitrary(
      for
        shardId <- shardIdArbitrary.arbitrary
        shardIteratorType <- Arbitrary.arbitrary[ShardIteratorType]
        startingSequenceNumber <- shardIteratorType match
          case ShardIteratorType.AFTER_SEQUENCE_NUMBER |
              ShardIteratorType.AT_SEQUENCE_NUMBER =>
            sequenceNumberArbitrary.arbitrary.map(x => Some(x))
          case _ => Gen.const(None)
        (streamName, streamArn) <- streamNameOrArnGen
        timestamp <- shardIteratorType match
          case ShardIteratorType.AT_TIMESTAMP => nowGen.map(x => Some(x))
          case _                              => Gen.const(None)
      yield GetShardIteratorRequest(
        shardId.shardId,
        shardIteratorType,
        startingSequenceNumber,
        streamName,
        streamArn,
        timestamp
      )
    )

  given getShardIteratorResponseArb: Arbitrary[GetShardIteratorResponse] =
    Arbitrary(
      shardIteratorGen.map(GetShardIteratorResponse.apply)
    )

  given increaseStreamRetentionRequestArb
      : Arbitrary[IncreaseStreamRetentionPeriodRequest] = Arbitrary(
    for
      retentionPeriodHours <- retentionPeriodHoursGen
      (streamName, streamArn) <- streamNameOrArnGen
    yield IncreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName,
      streamArn
    )
  )

  def nextTokenGen(exclusiveStartShardIndex: Option[Int]): Gen[String] = for
    streamName <- streamNameGen
    lastShardId <-
      Gen
        .choose(exclusiveStartShardIndex.getOrElse(0), 1000)
        .map(ShardId.create)
  yield ListShardsRequest.createNextToken(streamName, lastShardId.shardId)

  def shardFilterGen(exclusiveStartShardIndex: Option[Int]): Gen[ShardFilter] =
    for
      shardFilterType <- Arbitrary.arbitrary[ShardFilterType]
      shardFilterShardId <- shardFilterType match
        case ShardFilterType.AFTER_SHARD_ID =>
          Gen
            .choose(exclusiveStartShardIndex.getOrElse(0), 1000)
            .map(ShardId.create)
            .map(x => Some(x.shardId))
        case _ => Gen.const(None)
      shardFilterTimestamp <- shardFilterType match
        case ShardFilterType.AT_TIMESTAMP => nowGen.map(x => Some(x))
        case _                            => Gen.const(None)
      shardFilter <-
        Gen.const(
          ShardFilter(shardFilterShardId, shardFilterTimestamp, shardFilterType)
        )
    yield shardFilter

  given shardFilterArbitrary: Arbitrary[ShardFilter] = Arbitrary(
    shardFilterGen(None)
  )

  given listShardsRequestArb: Arbitrary[ListShardsRequest] = Arbitrary(
    for
      exclusiveStartShardId <- Gen.option(shardIdArbitrary.arbitrary)
      maxResults <- Gen.option(Gen.choose(1, 10000))
      nextToken <- Gen.option(nextTokenGen(exclusiveStartShardId.map(_.index)))
      shardFilter <- Gen.option(
        shardFilterGen(exclusiveStartShardId.map(_.index))
      )
      streamCreationTimestamp <- Gen.option(nowGen)
      streamName <- Gen.option(streamNameGen)
      streamArn <-
        if streamName.isEmpty then Gen.option(streamArnGen) else Gen.const(None)
    yield ListShardsRequest(
      exclusiveStartShardId.map(_.shardId),
      maxResults,
      nextToken,
      shardFilter,
      streamCreationTimestamp,
      streamName,
      streamArn
    )
  )

  given listShardsResponseArb: Arbitrary[ListShardsResponse] = Arbitrary(
    for
      nextToken <- Gen.option(nextTokenGen(None))
      shards <- Gen.sequence[Vector[ShardSummary], ShardSummary](
        Vector.range(0, 100).map(x => shardSummaryGen(x))
      )
    yield ListShardsResponse(nextToken, shards)
  )

  given listStreamConsumersRequestArb: Arbitrary[ListStreamConsumersRequest] =
    Arbitrary(
      for
        maxResults <- Gen.option(limitGen)
        nextToken <- Gen.option(consumerNameGen)
        streamArn <- streamArnGen
        streamCreationTimestamp <- Gen.option(nowGen)
      yield ListStreamConsumersRequest(
        maxResults,
        nextToken,
        streamArn,
        streamCreationTimestamp
      )
    )

  given listStreamConsumersResponseArb: Arbitrary[ListStreamConsumersResponse] =
    Arbitrary(
      for
        size <- Gen.choose(0, 20)
        consumers <- Gen.containerOfN[Vector, ConsumerSummary](
          size,
          consumerSummaryArb.arbitrary
        )
        nextToken = consumers.lastOption.map(_.consumerName)
      yield ListStreamConsumersResponse(consumers, nextToken)
    )

  given listStreamsRequestArb: Arbitrary[ListStreamsRequest] = Arbitrary(
    for
      exclusiveStartStreamName <- Gen.option(streamNameGen)
      limit <- Gen.option(limitGen)
    yield ListStreamsRequest(exclusiveStartStreamName, limit)
  )

  given listStreamsResponseArb: Arbitrary[ListStreamsResponse] =
    Arbitrary(
      for
        hasMoreStreams <- Arbitrary.arbitrary[Boolean]
        size <- Gen.choose(0, 50)
        streamNames <- Gen.containerOfN[Vector, StreamName](size, streamNameGen)
      yield ListStreamsResponse(hasMoreStreams, streamNames)
    )

  given listTagsForStreamRequestArb: Arbitrary[ListTagsForStreamRequest] =
    Arbitrary(
      for
        exclusiveStartTagKey <- Gen.option(tagKeyGen)
        limit <- Gen.option(limitGen)
        (streamName, streamArn) <- streamNameOrArnGen
      yield ListTagsForStreamRequest(
        exclusiveStartTagKey,
        limit,
        streamName,
        streamArn
      )
    )

  given listTagsForStreamResponseArb: Arbitrary[ListTagsForStreamResponse] =
    Arbitrary(
      for
        hasMoreTags <- Arbitrary.arbitrary[Boolean]
        tags <- tagListArb.arbitrary
      yield ListTagsForStreamResponse(hasMoreTags, tags)
    )

  given mergeShardsRequestArb: Arbitrary[MergeShardsRequest] = Arbitrary(
    for
      adjacentShardToMerge <- shardIdArbitrary.arbitrary
      shardToMerge <- shardIdArbitrary.arbitrary.suchThat(
        _ != adjacentShardToMerge
      )
      (streamName, streamArn) <- streamNameOrArnGen
    yield MergeShardsRequest(
      adjacentShardToMerge.shardId,
      shardToMerge.shardId,
      streamName,
      streamArn
    )
  )

  val explicitHashKeyGen: Gen[String] =
    Gen.choose(Shard.minHashKey, Shard.maxHashKey).map(_.toString)
  val partitionKeyGen: Gen[String] =
    Gen.choose(1, 256).flatMap(size => Gen.stringOfN(size, Gen.alphaNumChar))

  given putRecordRequestArb: Arbitrary[PutRecordRequest] = Arbitrary(
    for
      data <- dataGen
      explicitHashKey <- Gen.option(explicitHashKeyGen)
      partitionKey <- partitionKeyGen
      sequenceNumberForOrdering <- Gen.option(sequenceNumberArbitrary.arbitrary)
      nameOrArn <- Gen.choose(1, 2)
      streamName <-
        if nameOrArn == 1 then streamNameGen.map(Some(_)) else Gen.const(None)
      streamArn <-
        if nameOrArn == 2 then streamArnGen.map(Some(_)) else Gen.const(None)
    yield PutRecordRequest(
      data,
      explicitHashKey,
      partitionKey,
      sequenceNumberForOrdering,
      streamName,
      streamArn
    )
  )

  given putRecordResponseArb: Arbitrary[PutRecordResponse] = Arbitrary(
    for
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      sequenceNumber <- sequenceNumberArbitrary.arbitrary
      shardId <- shardIdArbitrary.arbitrary
    yield PutRecordResponse(encryptionType, sequenceNumber, shardId.shardId)
  )

  given putRecordsRequestEntryArb: Arbitrary[PutRecordsRequestEntry] =
    Arbitrary(
      for
        data <- dataGen
        explicitHashKey <- Gen.option(explicitHashKeyGen)
        partitionKey <- partitionKeyGen
      yield PutRecordsRequestEntry(data, explicitHashKey, partitionKey)
    )

  given putRecordsRequestArb: Arbitrary[PutRecordsRequest] = Arbitrary(
    for
      recordsSize <- Gen.choose(0, 500)
      records <- Gen.containerOfN[Vector, PutRecordsRequestEntry](
        recordsSize,
        putRecordsRequestEntryArb.arbitrary
      )
      nameOrArn <- Gen.choose(1, 2)
      streamName <-
        if nameOrArn == 1 then streamNameGen.map(Some(_)) else Gen.const(None)
      streamArn <-
        if nameOrArn == 2 then streamArnGen.map(Some(_)) else Gen.const(None)
    yield PutRecordsRequest(records, streamName, streamArn)
  )

  given putRecordsResultEntry: Arbitrary[PutRecordsResultEntry] =
    Arbitrary(
      for
        errorCode <- Gen.option(Arbitrary.arbitrary[PutRecordsErrorCode])
        errorMessage <- errorCode match
          case Some(_) => Gen.stringOfN(256, Gen.alphaNumChar).map(Some(_))
          case None    => Gen.const(None)
        sequenceNumber <- errorCode match
          case Some(_) => Gen.const(None)
          case None    => sequenceNumberArbitrary.arbitrary.map(Some(_))
        shardId <- errorCode match
          case Some(_) => Gen.const(None)
          case None    => Gen.option(shardIdArbitrary.arbitrary.map(_.shardId))
      yield PutRecordsResultEntry(
        errorCode,
        errorMessage,
        sequenceNumber,
        shardId
      )
    )

  given putRecordsResponseArb: Arbitrary[PutRecordsResponse] = Arbitrary(
    for
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      failedRecordCount <- Gen.choose(0, 500)
      recordsSize <- Gen.choose(failedRecordCount, 500)
      records <- Gen.containerOfN[Vector, PutRecordsResultEntry](
        recordsSize,
        putRecordsResultEntry.arbitrary
      )
    yield PutRecordsResponse(encryptionType, failedRecordCount, records)
  )

  given registerStreamConsumerRequestArb
      : Arbitrary[RegisterStreamConsumerRequest] = Arbitrary(
    for
      consumerName <- consumerNameGen
      streamArn <- streamArnGen
    yield RegisterStreamConsumerRequest(consumerName, streamArn)
  )

  given registerStreamConsumerResponseArb
      : Arbitrary[RegisterStreamConsumerResponse] = Arbitrary(
    consumerSummaryArb.arbitrary.map(RegisterStreamConsumerResponse.apply)
  )

  given shardIteratorArbitrary: Arbitrary[ShardIterator] = Arbitrary(
    shardIteratorGen
  )

  given splitShardRequestArb: Arbitrary[SplitShardRequest] = Arbitrary(
    for
      newStartingHashKey <- hashKeyRangeArbitrary.arbitrary.map(
        _.startingHashKey.toString
      )
      shardToSplit <- shardIdArbitrary.arbitrary.map(_.shardId)
      (streamName, streamArn) <- streamNameOrArnGen
    yield SplitShardRequest(
      newStartingHashKey,
      shardToSplit,
      streamName,
      streamArn
    )
  )

  given startStreamEncryptionRequestArb
      : Arbitrary[StartStreamEncryptionRequest] = Arbitrary(
    for
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      keyId <- keyIdGen
      (streamName, streamArn) <- streamNameOrArnGen
    yield StartStreamEncryptionRequest(
      encryptionType,
      keyId,
      streamName,
      streamArn
    )
  )

  given stopStreamEncryptionRequestArb: Arbitrary[StopStreamEncryptionRequest] =
    Arbitrary(
      for
        encryptionType <- Arbitrary.arbitrary[EncryptionType]
        keyId <- keyIdGen
        (streamName, streamArn) <- streamNameOrArnGen
      yield StopStreamEncryptionRequest(
        encryptionType,
        keyId,
        streamName,
        streamArn
      )
    )

  given updateShardCountRequestArb: Arbitrary[UpdateShardCountRequest] =
    Arbitrary(
      for
        scalingType <- Arbitrary.arbitrary[ScalingType]
        (streamName, streamArn) <- streamNameOrArnGen
        targetShardCount <- Gen.choose(1, 1000)
      yield UpdateShardCountRequest(
        scalingType,
        streamName,
        streamArn,
        targetShardCount
      )
    )

  given updateShardCountResponseArb: Arbitrary[UpdateShardCountResponse] =
    Arbitrary(
      for
        streamName <- streamNameGen
        targetShardCount <- Gen.choose(1, 1000)
        currentShardCount <- Gen.choose(1, 1000)
      yield UpdateShardCountResponse(
        currentShardCount,
        streamName,
        targetShardCount
      )
    )

  given streamDataArbitrary: Arbitrary[StreamData] = Arbitrary(
    for
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
      streamModeDetails <- streamModeDetailsArb.arbitrary
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
    yield StreamData(
      consumers,
      encryptionType,
      enhancedMonitoring,
      keyId,
      retentionPeriod,
      shards,
      streamArn,
      streamCreationTimestamp,
      streamModeDetails,
      streamName,
      streamStatus,
      tags,
      shardCountUpdates
    )
  )

  given streamsArb: Arbitrary[Streams] = Arbitrary {
    Gen
      .choose(0, 2)
      .flatMap(size => Gen.listOfN(size, streamDataArbitrary.arbitrary))
      .suchThat(x =>
        x.groupBy(_.streamArn).filter { case (_, x) => x.length > 1 }.isEmpty
      )
      .map(x => Streams(SortedMap.from(x.map(sd => sd.streamArn -> sd))))
  }

  given updateStreamModeRequestArb: Arbitrary[UpdateStreamModeRequest] =
    Arbitrary {
      for
        streamArn <- streamArnGen
        streamModeDetails <- streamModeDetailsArb.arbitrary
      yield UpdateStreamModeRequest(streamArn, streamModeDetails)
    }
