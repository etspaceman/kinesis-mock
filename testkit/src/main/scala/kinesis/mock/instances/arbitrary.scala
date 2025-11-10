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
  given Arbitrary[AwsAccountId] = Arbitrary(
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

  given Arbitrary[StreamName] = Arbitrary(
    streamNameGen
  )

  val streamArnGen: Gen[StreamArn] = for
    streamName <- streamNameGen
    awsAccountId <- awsAccountIdGen
    awsRegion <- Arbitrary.arbitrary[AwsRegion]
  yield StreamArn(awsRegion, streamName, awsAccountId)

  given Arbitrary[StreamArn] = Arbitrary(streamArnGen)

  val streamNameOrArnGen: Gen[(Option[StreamName], Option[StreamArn])] = for
    streamName <- Gen.option(streamNameGen)
    streamArn <-
      if streamName.isEmpty then streamArnGen.map(Some(_)) else Gen.const(None)
  yield (streamName, streamArn)

  val nowGen: Gen[Instant] = Gen.delay(Gen.const(Instant.now))

  given Arbitrary[SequenceNumber] = Arbitrary(
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

  given Arbitrary[ConsumerName] = Arbitrary(
    consumerNameGen
  )

  val consumerArnGen: Gen[ConsumerArn] = for
    streamArn <- streamArnGen
    consumerName <- consumerNameGen
    consumerCreationTimestamp <- nowGen
  yield ConsumerArn(streamArn, consumerName, consumerCreationTimestamp)

  given Arbitrary[ConsumerArn] = Arbitrary(
    consumerArnGen
  )

  given Arbitrary[Consumer] = Arbitrary(
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

  given Arbitrary[ConsumerSummary] = Arbitrary(
    Arbitrary.arbitrary[Consumer].map(ConsumerSummary.fromConsumer)
  )

  given Arbitrary[HashKeyRange] = Arbitrary(
    for
      startingHashKey <- Gen.posNum[Int].map(BigInt.apply)
      endingHashKey <- Gen.posNum[Int].map(i => BigInt(i) + startingHashKey)
    yield HashKeyRange(startingHashKey, endingHashKey)
  )

  val dataGen: Gen[Array[Byte]] =
    Arbitrary.arbitrary[Array[Byte]].suchThat(_.length < 1048576)

  given Arbitrary[KinesisRecord] = Arbitrary(
    for
      approximateArrivalTimestamp <- nowGen
      data <- dataGen
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      partitionKey <- Gen
        .choose(1, 256)
        .flatMap(size => Gen.stringOfN(size, Gen.alphaNumChar))
      sequenceNumber <- Arbitrary.arbitrary[SequenceNumber]
    yield KinesisRecord(
      approximateArrivalTimestamp,
      data,
      encryptionType,
      partitionKey,
      sequenceNumber
    )
  )

  given Arbitrary[SequenceNumberRange] =
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

  given Arbitrary[ShardLevelMetrics] =
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
    hashKeyRange <- Arbitrary.arbitrary[HashKeyRange]
    sequenceNumberRange <- Arbitrary.arbitrary[SequenceNumberRange]
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

  given Arbitrary[Shard] = Arbitrary(
    Gen.choose(100, 1000).flatMap(index => shardGen(index))
  )

  given Arbitrary[ShardSummary] = Arbitrary(
    Arbitrary.arbitrary[Shard].map(ShardSummary.fromShard)
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

  given Arbitrary[TagList] = Arbitrary(
    tagsGen.map(TagList.fromTags)
  )

  given Arbitrary[TagListEntry] = Arbitrary(
    for
      key <- tagKeyGen
      value <- tagValueGen
    yield TagListEntry(key, value)
  )

  given Arbitrary[Tags] = Arbitrary(tagsGen)

  given Arbitrary[AddTagsToStreamRequest] =
    Arbitrary(
      for
        (streamName, streamArn) <- streamNameOrArnGen
        tags <- tagsGen
      yield AddTagsToStreamRequest(streamName, streamArn, tags)
    )

  given Arbitrary[StreamModeDetails] = Arbitrary(
    Arbitrary.arbitrary[StreamMode].map(StreamModeDetails.apply)
  )

  given Arbitrary[CreateStreamRequest] =
    Arbitrary(
      for
        shardCount <- Gen.option(Gen.choose(1, 1000))
        streamName <- streamNameGen
        streamModeDetails <- Gen.option(Arbitrary.arbitrary[StreamModeDetails])
      yield CreateStreamRequest(shardCount, streamModeDetails, streamName)
    )

  val retentionPeriodHoursGen: Gen[Int] = Gen.choose(
    StreamData.minRetentionPeriod.toHours.toInt,
    StreamData.maxRetentionPeriod.toHours.toInt
  )

  given Arbitrary[DecreaseStreamRetentionPeriodRequest] = Arbitrary(
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

  given Arbitrary[DeregisterStreamConsumerRequest] = Arbitrary(
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

  given Arbitrary[DescribeLimitsResponse] =
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

  given Arbitrary[StreamDescription] = Arbitrary(
    for
      encryptionType <- Gen.option(Arbitrary.arbitrary[EncryptionType])
      enhancedMonitoring <- Gen
        .choose(0, 1)
        .flatMap(size =>
          Gen.containerOfN[Vector, ShardLevelMetrics](
            size,
            Arbitrary.arbitrary[ShardLevelMetrics]
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
      streamModeDetails <- Arbitrary.arbitrary[StreamModeDetails]
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

  given Arbitrary[ShardId] = Arbitrary(
    Gen.choose(0, 1000).map(index => ShardId.create(index))
  )

  given Arbitrary[DescribeStreamRequest] =
    Arbitrary(
      for
        exclusiveStartShardId <- Gen.option(
          Arbitrary.arbitrary[ShardId].map(_.shardId)
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

  given Arbitrary[DescribeStreamResponse] =
    Arbitrary(
      Arbitrary.arbitrary[StreamDescription].map(DescribeStreamResponse.apply)
    )

  given Arbitrary[DescribeStreamSummaryRequest] = Arbitrary(
    streamNameOrArnGen.map { case (streamName, streamArn) =>
      DescribeStreamSummaryRequest(streamName, streamArn)
    }
  )

  given Arbitrary[StreamDescriptionSummary] =
    Arbitrary(
      for
        consumerCount <- Gen.option(Gen.choose(1, 20))
        encryptionType <- Gen.option(Arbitrary.arbitrary[EncryptionType])
        enhancedMonitoring <- Gen
          .choose(0, 1)
          .flatMap(size =>
            Gen.containerOfN[Vector, ShardLevelMetrics](
              size,
              Arbitrary.arbitrary[ShardLevelMetrics]
            )
          )
        keyId <- Gen.option(keyIdGen)
        openShardCount <- Gen.choose(1, 50)
        retentionPeriodHours <- retentionPeriodHoursGen
        streamCreationTimestamp <- nowGen
        streamModeDetails <- Arbitrary.arbitrary[StreamModeDetails]
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

  given Arbitrary[DescribeStreamSummaryResponse] = Arbitrary(
    Arbitrary
      .arbitrary[StreamDescriptionSummary]
      .map(
        DescribeStreamSummaryResponse.apply
      )
  )

  given Arbitrary[DisableEnhancedMonitoringRequest] = Arbitrary(
    for
      shardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
          _.shardLevelMetrics
        )
      (streamName, streamArn) <- streamNameOrArnGen
    yield DisableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  )

  given Arbitrary[DisableEnhancedMonitoringResponse] = Arbitrary(
    for
      currentShardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
          _.shardLevelMetrics
        )
      desiredShardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
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

  given Arbitrary[EnableEnhancedMonitoringRequest] = Arbitrary(
    for
      shardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
          _.shardLevelMetrics
        )
      (streamName, streamArn) <- streamNameOrArnGen
    yield EnableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  )

  given Arbitrary[EnableEnhancedMonitoringResponse] = Arbitrary(
    for
      currentShardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
          _.shardLevelMetrics
        )
      desiredShardLevelMetrics <- Arbitrary
        .arbitrary[ShardLevelMetrics]
        .map(
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
    shardId <- Arbitrary.arbitrary[ShardId]
    sequenceNumber <- Arbitrary.arbitrary[SequenceNumber]
    now <- nowGen
  yield ShardIterator.create(streamName, shardId.shardId, sequenceNumber, now)

  given Arbitrary[GetRecordsRequest] = Arbitrary(
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
    hashKeyRange <- Arbitrary.arbitrary[HashKeyRange]
  yield ChildShard(hashKeyRange, parentShards, shardId)

  given Arbitrary[GetRecordsResponse] = Arbitrary(
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
            Arbitrary.arbitrary[KinesisRecord]
          )
        )
    yield GetRecordsResponse(
      childShards,
      millisBehindLatest,
      nextShardIterator,
      records
    )
  )

  given Arbitrary[GetShardIteratorRequest] =
    Arbitrary(
      for
        shardId <- Arbitrary.arbitrary[ShardId]
        shardIteratorType <- Arbitrary.arbitrary[ShardIteratorType]
        startingSequenceNumber <- shardIteratorType match
          case ShardIteratorType.AFTER_SEQUENCE_NUMBER |
              ShardIteratorType.AT_SEQUENCE_NUMBER =>
            Arbitrary.arbitrary[SequenceNumber].map(x => Some(x))
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

  given Arbitrary[GetShardIteratorResponse] =
    Arbitrary(
      shardIteratorGen.map(GetShardIteratorResponse.apply)
    )

  given Arbitrary[IncreaseStreamRetentionPeriodRequest] = Arbitrary(
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

  given Arbitrary[ShardFilter] = Arbitrary(
    shardFilterGen(None)
  )

  given Arbitrary[ListShardsRequest] = Arbitrary(
    for
      exclusiveStartShardId <- Gen.option(Arbitrary.arbitrary[ShardId])
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

  given Arbitrary[ListShardsResponse] = Arbitrary(
    for
      nextToken <- Gen.option(nextTokenGen(None))
      shards <- Gen.sequence[Vector[ShardSummary], ShardSummary](
        Vector.range(0, 100).map(x => shardSummaryGen(x))
      )
    yield ListShardsResponse(nextToken, shards)
  )

  given Arbitrary[ListStreamConsumersRequest] =
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

  given Arbitrary[ListStreamConsumersResponse] =
    Arbitrary(
      for
        size <- Gen.choose(0, 20)
        consumers <- Gen.containerOfN[Vector, ConsumerSummary](
          size,
          Arbitrary.arbitrary[ConsumerSummary]
        )
        nextToken = consumers.lastOption.map(_.consumerName)
      yield ListStreamConsumersResponse(consumers, nextToken)
    )

  given Arbitrary[ListStreamsRequest] = Arbitrary(
    for
      exclusiveStartStreamName <- Gen.option(streamNameGen)
      limit <- Gen.option(limitGen)
    yield ListStreamsRequest(exclusiveStartStreamName, limit)
  )

  given Arbitrary[ListStreamsResponse] =
    Arbitrary(
      for
        hasMoreStreams <- Arbitrary.arbitrary[Boolean]
        size <- Gen.choose(0, 50)
        streamNames <- Gen.containerOfN[Vector, StreamName](size, streamNameGen)
      yield ListStreamsResponse(hasMoreStreams, streamNames)
    )

  given Arbitrary[ListTagsForStreamRequest] =
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

  given Arbitrary[ListTagsForStreamResponse] =
    Arbitrary(
      for
        hasMoreTags <- Arbitrary.arbitrary[Boolean]
        tags <- Arbitrary.arbitrary[TagList]
      yield ListTagsForStreamResponse(hasMoreTags, tags)
    )

  given Arbitrary[MergeShardsRequest] = Arbitrary(
    for
      adjacentShardToMerge <- Arbitrary.arbitrary[ShardId]
      shardToMerge <- Arbitrary
        .arbitrary[ShardId]
        .suchThat(
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

  given Arbitrary[PutRecordRequest] = Arbitrary(
    for
      data <- dataGen
      explicitHashKey <- Gen.option(explicitHashKeyGen)
      partitionKey <- partitionKeyGen
      sequenceNumberForOrdering <- Gen.option(
        Arbitrary.arbitrary[SequenceNumber]
      )
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

  given Arbitrary[PutRecordResponse] = Arbitrary(
    for
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      sequenceNumber <- Arbitrary.arbitrary[SequenceNumber]
      shardId <- Arbitrary.arbitrary[ShardId]
    yield PutRecordResponse(encryptionType, sequenceNumber, shardId.shardId)
  )

  given Arbitrary[PutRecordsRequestEntry] =
    Arbitrary(
      for
        data <- dataGen
        explicitHashKey <- Gen.option(explicitHashKeyGen)
        partitionKey <- partitionKeyGen
      yield PutRecordsRequestEntry(data, explicitHashKey, partitionKey)
    )

  given Arbitrary[PutRecordsRequest] = Arbitrary(
    for
      recordsSize <- Gen.choose(0, 500)
      records <- Gen.containerOfN[Vector, PutRecordsRequestEntry](
        recordsSize,
        Arbitrary.arbitrary[PutRecordsRequestEntry]
      )
      nameOrArn <- Gen.choose(1, 2)
      streamName <-
        if nameOrArn == 1 then streamNameGen.map(Some(_)) else Gen.const(None)
      streamArn <-
        if nameOrArn == 2 then streamArnGen.map(Some(_)) else Gen.const(None)
    yield PutRecordsRequest(records, streamName, streamArn)
  )

  given Arbitrary[PutRecordsResultEntry] =
    Arbitrary(
      for
        errorCode <- Gen.option(Arbitrary.arbitrary[PutRecordsErrorCode])
        errorMessage <- errorCode match
          case Some(_) => Gen.stringOfN(256, Gen.alphaNumChar).map(Some(_))
          case None    => Gen.const(None)
        sequenceNumber <- errorCode match
          case Some(_) => Gen.const(None)
          case None    => Arbitrary.arbitrary[SequenceNumber].map(Some(_))
        shardId <- errorCode match
          case Some(_) => Gen.const(None)
          case None => Gen.option(Arbitrary.arbitrary[ShardId].map(_.shardId))
      yield PutRecordsResultEntry(
        errorCode,
        errorMessage,
        sequenceNumber,
        shardId
      )
    )

  given Arbitrary[PutRecordsResponse] = Arbitrary(
    for
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      failedRecordCount <- Gen.choose(0, 500)
      recordsSize <- Gen.choose(failedRecordCount, 500)
      records <- Gen.containerOfN[Vector, PutRecordsResultEntry](
        recordsSize,
        Arbitrary.arbitrary[PutRecordsResultEntry]
      )
    yield PutRecordsResponse(encryptionType, failedRecordCount, records)
  )

  given Arbitrary[RegisterStreamConsumerRequest] = Arbitrary(
    for
      consumerName <- consumerNameGen
      streamArn <- streamArnGen
    yield RegisterStreamConsumerRequest(consumerName, streamArn)
  )

  given Arbitrary[RegisterStreamConsumerResponse] = Arbitrary(
    Arbitrary
      .arbitrary[ConsumerSummary]
      .map(RegisterStreamConsumerResponse.apply)
  )

  given Arbitrary[ShardIterator] = Arbitrary(
    shardIteratorGen
  )

  given Arbitrary[SplitShardRequest] = Arbitrary(
    for
      newStartingHashKey <- Arbitrary
        .arbitrary[HashKeyRange]
        .map(
          _.startingHashKey.toString
        )
      shardToSplit <- Arbitrary.arbitrary[ShardId].map(_.shardId)
      (streamName, streamArn) <- streamNameOrArnGen
    yield SplitShardRequest(
      newStartingHashKey,
      shardToSplit,
      streamName,
      streamArn
    )
  )

  given Arbitrary[StartStreamEncryptionRequest] = Arbitrary(
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

  given Arbitrary[StopStreamEncryptionRequest] =
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

  given Arbitrary[UpdateShardCountRequest] =
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

  given Arbitrary[UpdateShardCountResponse] =
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
          Arbitrary.arbitrary[Consumer].map(x => x.consumerName -> x)
        )
        .map(x => SortedMap.from(x))
      encryptionType <- Arbitrary.arbitrary[EncryptionType]
      enhancedMonitoring <- Gen
        .choose(0, 1)
        .flatMap(size =>
          Gen.containerOfN[Vector, ShardLevelMetrics](
            size,
            Arbitrary.arbitrary[ShardLevelMetrics]
          )
        )
      keyId <- Gen.option(keyIdGen)
      retentionPeriod <- retentionPeriodHoursGen.map(_.hours)
      shardsSize <- Gen.choose(0, 50)
      shardList <- Gen
        .containerOfN[Vector, Shard](shardsSize, Arbitrary.arbitrary[Shard])
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
                  Arbitrary.arbitrary[KinesisRecord]
                )
                .map(records => shard -> records)
            )
        )
      )
      streamModeDetails <- Arbitrary.arbitrary[StreamModeDetails]
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

  given Arbitrary[Streams] = Arbitrary {
    Gen
      .choose(0, 2)
      .flatMap(size => Gen.listOfN(size, streamDataArbitrary.arbitrary))
      .suchThat(x =>
        x.groupBy(_.streamArn).filter { case (_, x) => x.length > 1 }.isEmpty
      )
      .map(x => Streams(SortedMap.from(x.map(sd => sd.streamArn -> sd))))
  }

  given Arbitrary[UpdateStreamModeRequest] =
    Arbitrary {
      for
        streamArn <- streamArnGen
        streamModeDetails <- Arbitrary.arbitrary[StreamModeDetails]
      yield UpdateStreamModeRequest(streamArn, streamModeDetails)
    }
