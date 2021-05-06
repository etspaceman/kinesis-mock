package kinesis.mock
package validations

import scala.util.Try

import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import software.amazon.awssdk.utils.Md5Utils

import kinesis.mock.models._

object CommonValidations {
  def validateStreamName(
      streamName: StreamName
  ): ValidatedResponse[StreamName] =
    (
      if (!streamName.streamName.matches("[a-zA-Z0-9_.-]+"))
        InvalidArgumentException(
          s"Stream Name '$streamName' contains invalid characters"
        ).invalidNel
      else Valid(()),
      if (streamName.streamName.isEmpty || streamName.streamName.length() > 128)
        InvalidArgumentException(
          s"Stream name must be between 1 and 128 characters. Invalid stream name: $streamName"
        ).invalidNel
      else Valid(())
    ).mapN((_, _) => streamName)

  def validateStreamArn(
      streamArn: String
  ): ValidatedResponse[String] = (
    if (!streamArn.matches("arn:aws.*:kinesis:.*:\\d{12}:stream/.+"))
      InvalidArgumentException(
        s"StreamARN '$streamArn' is not formatted properly"
      ).invalidNel
    else Valid(()),
    if (streamArn.isEmpty || streamArn.length() > 2048)
      InvalidArgumentException(
        s"StreamARN must be between 1 and 2048 characters. Invalid StreamARN: $streamArn"
      ).invalidNel
    else Valid(())
  ).mapN((_, _) => streamArn)

  def findStream(
      streamName: StreamName,
      streams: Streams
  ): ValidatedResponse[StreamData] =
    streams.streams
      .get(streamName)
      .toValidNel(
        ResourceNotFoundException(s"Stream name $streamName not found")
      )

  def findStreamByArn(
      streamArn: String,
      streams: Streams
  ): ValidatedResponse[StreamData] = streams.streams.values
    .find(_.streamArn == streamArn)
    .toValidNel(ResourceNotFoundException(s"StreamARN $streamArn not found"))

  def findStreamByConsumerArn(
      consumerArn: String,
      streams: Streams
  ): ValidatedResponse[(Consumer, StreamData)] =
    streams.streams.values
      .find(_.consumers.values.exists(_.consumerArn == consumerArn))
      .flatMap(stream =>
        stream.consumers.values
          .find(_.consumerArn == consumerArn)
          .map(consumer => (consumer, stream))
      )
      .toValidNel(
        ResourceNotFoundException(s"ConsumerARN $consumerArn not found")
      )

  def isStreamActive(
      streamName: StreamName,
      streams: Streams
  ): ValidatedResponse[StreamName] =
    if (
      streams.streams
        .get(streamName)
        .exists(_.streamStatus != StreamStatus.ACTIVE)
    )
      ResourceInUseException(
        s"Stream $streamName is not currently ACTIVE."
      ).invalidNel
    else streamName.valid

  def isStreamActiveOrUpdating(
      streamName: StreamName,
      streams: Streams
  ): ValidatedResponse[StreamName] =
    if (
      streams.streams
        .get(streamName)
        .exists(x =>
          x.streamStatus != StreamStatus.ACTIVE && x.streamStatus != StreamStatus.UPDATING
        )
    )
      ResourceInUseException(
        s"Stream $streamName is not currently ACTIVE or UPDATING."
      ).invalidNel
    else streamName.valid

  def validateShardLimit(
      shardCountToAdd: Int,
      streams: Streams,
      shardLimit: Int
  ): ValidatedResponse[Int] =
    if (
      streams.streams.values.map(_.shards.keys.count(_.isOpen)).sum +
        shardCountToAdd > shardLimit
    )
      LimitExceededException(
        s"Request would exceed the shard limit of $shardLimit"
      ).invalidNel
    else shardCountToAdd.valid

  def validateShardCount(
      shardCount: Int
  ): ValidatedResponse[Int] =
    if (shardCount < 1 || shardCount > 1000)
      LimitExceededException(
        s"The shard count must be between 1 and 1000"
      ).invalidNel
    else shardCount.valid

  def validateTagKeys(
      keys: Iterable[String]
  ): ValidatedResponse[Iterable[String]] =
    (
      {
        val startsWithAws = keys.filter(_.startsWith("aws:"))
        if (startsWithAws.nonEmpty)
          InvalidArgumentException(
            s"Cannot start tags with 'aws:'. Invalid keys: ${startsWithAws.mkString(", ")}"
          ).invalidNel
        else Valid(())
      }, {
        val keysTooLong = keys.filter(x => x.isEmpty || x.length > 128)
        if (keysTooLong.nonEmpty)
          InvalidArgumentException(
            s"Tags must be between 1 and 128 characters. Invalid keys: ${keysTooLong.mkString(", ")}"
          ).invalidNel
        else Valid(())
      }, {
        val invalidKeyCharacters =
          keys.filterNot(x => x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$"))
        if (invalidKeyCharacters.nonEmpty)
          InvalidArgumentException(
            s"Keys contain invalid characters. Invalid keys: ${invalidKeyCharacters.mkString(", ")}"
          ).invalidNel
        else Valid(())
      }
    ).mapN((_, _, _) => keys)

  def validateRetentionPeriodHours(
      retentionPeriodHours: Int
  ): ValidatedResponse[Int] =
    if (
      retentionPeriodHours < StreamData.minRetentionPeriod.toHours || retentionPeriodHours > StreamData.maxRetentionPeriod.toHours
    )
      InvalidArgumentException(
        s"Retention period hours $retentionPeriodHours must be between ${StreamData.minRetentionPeriod.toHours} and ${StreamData.maxRetentionPeriod.toHours}"
      ).invalidNel
    else Valid(retentionPeriodHours)

  def validateShardId(
      shardId: String
  ): ValidatedResponse[String] =
    (
      if (!shardId.matches("[a-zA-Z0-9_.-]+"))
        InvalidArgumentException(
          s"Shard ID '$shardId' contains invalid characters"
        ).invalidNel
      else Valid(()),
      if (shardId.isEmpty || shardId.length() > 128)
        InvalidArgumentException(
          s"Shard ID must be between 1 and 128 characters. Invalid Shard ID: $shardId"
        ).invalidNel
      else Valid(())
    ).mapN((_, _) => shardId)

  def validateConsumerName(
      consumerName: ConsumerName
  ): ValidatedResponse[ConsumerName] = (
    if (!consumerName.consumerName.matches("[a-zA-Z0-9_.-]+"))
      InvalidArgumentException(
        s"ConsumerName '$consumerName' contains invalid characters"
      ).invalidNel
    else Valid(()),
    if (
      consumerName.consumerName.isEmpty || consumerName.consumerName
        .length() > 128
    )
      InvalidArgumentException(
        s"ConsumerName must be between 1 and 128 characters. Invalid ConsumerName: $consumerName"
      ).invalidNel
    else Valid(())
  ).mapN((_, _) => consumerName)

  def findConsumer(
      consumerName: ConsumerName,
      streamData: StreamData
  ): ValidatedResponse[Consumer] =
    streamData.consumers
      .get(consumerName)
      .toValidNel(
        ResourceNotFoundException(
          s"ConsumerName $consumerName not found on stream ${streamData.streamName}"
        )
      )

  def validateNextToken(
      nextToken: String
  ): ValidatedResponse[String] =
    if (nextToken.isEmpty || nextToken.length() > 1048576)
      InvalidArgumentException(
        s"NextToken length must be between 1 and 1048576"
      ).invalidNel
    else Valid(nextToken)

  def validateMaxResults(
      maxResults: Int
  ): ValidatedResponse[Int] =
    if (maxResults < 1 || maxResults > 10000)
      InvalidArgumentException(
        s"MaxResults must be between 1 and 10000"
      ).invalidNel
    else Valid(maxResults)

  def validateLimit(
      limit: Int
  ): ValidatedResponse[Int] =
    if (limit < 1 || limit > 10000)
      InvalidArgumentException(
        s"Limit must be between 1 and 10000"
      ).invalidNel
    else Valid(limit)

  def validateKeyId(keyId: String): ValidatedResponse[String] =
    if (
      keyId.startsWith("arn:") && (
        (
          keyId.matches("arn:aws.*:kms:.*:\\d{12}:key/.+") &&
            Try(UUID.fromString(keyId.takeRight(36))).isFailure
        ) ||
          (
            !keyId.matches("arn:aws.*:kms:.*:\\d{12}:alias/.+") &&
              !keyId.matches("arn:aws.*:kms:.*:\\d{12}:key/.+")
          )
      )
    )
      InvalidArgumentException(
        "Received KeyId ARN is not a properly formatted ARN"
      ).invalidNel
    else if (
      !keyId.startsWith("alias/") &&
      !keyId.startsWith("arn:") &&
      Try(UUID.fromString(keyId.takeRight(36))).isFailure
    ) {
      InvalidArgumentException(
        "Received KeyId is not a properly formatted Alias or GUID"
      ).invalidNel
    } else if (keyId.isEmpty || keyId.length() > 2048)
      InvalidArgumentException(
        "KeyId must be between 1 and 2048 characters"
      ).invalidNel
    else Valid(keyId)

  def isKmsEncryptionType(
      encryptionType: EncryptionType
  ): ValidatedResponse[EncryptionType] =
    encryptionType match {
      case EncryptionType.KMS => Valid(encryptionType)
      case _ =>
        InvalidArgumentException(
          "KMS is the only valid EncryptionType for this request"
        ).invalidNel
    }

  def validateSequenceNumber(
      sequenceNumber: SequenceNumber
  ): ValidatedResponse[SequenceNumber] =
    if (
      SequenceNumberConstant
        .withNameOption(sequenceNumber.value)
        .isEmpty && !sequenceNumber.value.matches("0|([1-9]\\d{0,128})")
    )
      InvalidArgumentException(
        s"SequenceNumber ${sequenceNumber.value} contains invalid characters"
      ).invalidNel
    else Valid(sequenceNumber)

  def findShard(
      shardId: String,
      stream: StreamData
  ): ValidatedResponse[(Shard, List[KinesisRecord])] =
    stream.shards.find { case (shard, _) =>
      shard.shardId.shardId == shardId
    } match {
      case None =>
        ResourceNotFoundException(
          s"Could not find shardId $shardId in stream ${stream.streamName}"
        ).invalidNel
      case Some(x) => Valid(x)
    }

  def computeShard(
      partitionKey: String,
      explicitHashKey: Option[String],
      stream: StreamData
  ): ValidatedResponse[(Shard, List[KinesisRecord])] = {
    (explicitHashKey match {
      case Some(ehk) =>
        val hash = BigInt(ehk)
        if (hash < Shard.minHashKey || hash > Shard.maxHashKey) {
          InvalidArgumentException("ExplicitHashKey is not valid").invalidNel
        } else {
          hash.validNel
        }
      case None =>
        Try(
          Md5Utils.computeMD5Hash(partitionKey.getBytes(StandardCharsets.UTF_8))
        ).toValidated.bimap(
          e =>
            NonEmptyList.one(
              InvalidArgumentException(
                s"Could not compute MD5 hash, ${e.getMessage}"
              )
            ),
          x => BigInt(1, x)
        )
    }).andThen { hashInt =>
      stream.shards
        .collectFirst {
          case (shard, data)
              if shard.isOpen && hashInt >= shard.hashKeyRange.startingHashKey && hashInt <= shard.hashKeyRange.endingHashKey =>
            (shard, data)
        } match {
        case None =>
          InvalidArgumentException(
            "Could not find shard for partitionKey"
          ).invalidNel
        case Some(x) => Valid(x)
      }
    }
  }

  def validateExplicitHashKey(
      explicitHashKey: String
  ): ValidatedResponse[String] =
    if (!explicitHashKey.matches("0|([1-9]\\d{0,38})"))
      InvalidArgumentException(
        "ExplicitHashKey contains invalid characters"
      ).invalidNel
    else Valid(explicitHashKey)

  def validatePartitionKey(
      partitionKey: String
  ): ValidatedResponse[String] =
    if (partitionKey.isEmpty || partitionKey.length > 256)
      InvalidArgumentException(
        "Partition key must be between 1 and 256 in length"
      ).invalidNel
    else Valid(partitionKey)

  def isShardOpen(shard: Shard): ValidatedResponse[Shard] =
    if (!shard.isOpen)
      ResourceInUseException(s"Shard ${shard.shardId} is not active").invalidNel
    else Valid(shard)

  def validateData(
      data: Array[Byte]
  ): ValidatedResponse[Array[Byte]] =
    if (data.length > 1048576)
      InvalidArgumentException("Data object is too large").invalidNel
    else Valid(data)
}
