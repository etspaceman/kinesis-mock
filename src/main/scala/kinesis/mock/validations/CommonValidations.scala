package kinesis.mock
package validations

import scala.util.Try

import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.syntax.all._
import software.amazon.awssdk.utils.Md5Utils

import kinesis.mock.models._

object CommonValidations {
  def validateStreamName(
      streamName: StreamName
  ): Response[StreamName] =
    (
      if (!streamName.streamName.matches("[a-zA-Z0-9_.-]+"))
        InvalidArgumentException(
          s"Stream Name '$streamName' contains invalid characters"
        ).asLeft
      else Right(()),
      if (streamName.streamName.isEmpty || streamName.streamName.length() > 128)
        InvalidArgumentException(
          s"Stream name must be between 1 and 128 characters. Invalid stream name: $streamName"
        ).asLeft
      else Right(())
    ).mapN((_, _) => streamName)

  def validateStreamArn(
      streamArn: StreamArn
  ): Response[StreamArn] = (
    if (!streamArn.streamArn.matches("arn:aws.*:kinesis:.*:\\d{12}:stream/.+"))
      InvalidArgumentException(
        s"StreamARN '$streamArn' is not formatted properly"
      ).asLeft
    else Right(()),
    if (streamArn.streamArn.isEmpty || streamArn.streamArn.length() > 2048)
      InvalidArgumentException(
        s"StreamARN must be between 1 and 2048 characters. Invalid StreamARN: $streamArn"
      ).asLeft
    else Right(())
  ).mapN((_, _) => streamArn)

  def findStream(
      streamArn: StreamArn,
      streams: Streams
  ): Response[StreamData] =
    streams.streams
      .get(streamArn)
      .toRight(
        ResourceNotFoundException(s"Stream arn $streamArn not found")
      )

  def findStreamByConsumerArn(
      consumerArn: ConsumerArn,
      streams: Streams
  ): Response[(Consumer, StreamData)] =
    streams.streams.values
      .find(_.consumers.values.exists(_.consumerArn === consumerArn))
      .flatMap(stream =>
        stream.consumers.values
          .find(_.consumerArn === consumerArn)
          .map(consumer => (consumer, stream))
      )
      .toRight(
        ResourceNotFoundException(s"ConsumerARN $consumerArn not found")
      )

  def isStreamActive(
      streamArn: StreamArn,
      streams: Streams
  ): Response[StreamArn] =
    if (
      streams.streams
        .get(streamArn)
        .exists(_.streamStatus != StreamStatus.ACTIVE)
    )
      ResourceInUseException(
        s"Stream $streamArn is not currently ACTIVE."
      ).asLeft
    else streamArn.asRight

  def isStreamActiveOrUpdating(
      streamArn: StreamArn,
      streams: Streams
  ): Response[StreamArn] =
    if (
      streams.streams
        .get(streamArn)
        .exists(x =>
          x.streamStatus != StreamStatus.ACTIVE && x.streamStatus != StreamStatus.UPDATING
        )
    )
      ResourceInUseException(
        s"Stream $streamArn is not currently ACTIVE or UPDATING."
      ).asLeft
    else streamArn.asRight

  def validateShardLimit(
      shardCountToAdd: Int,
      streams: Streams,
      shardLimit: Int
  ): Response[Int] =
    if (
      streams.streams.values.map(_.shards.keys.count(_.isOpen)).sum +
        shardCountToAdd > shardLimit
    )
      LimitExceededException(
        s"Request would exceed the shard limit of $shardLimit"
      ).asLeft
    else shardCountToAdd.asRight

  def validateShardCount(
      shardCount: Int
  ): Response[Int] =
    if (shardCount < 1 || shardCount > 1000)
      LimitExceededException(
        s"The shard count must be between 1 and 1000"
      ).asLeft
    else shardCount.asRight

  def validateTagKeys(
      keys: Iterable[String]
  ): Response[Iterable[String]] =
    (
      {
        val startsWithAws = keys.filter(_.startsWith("aws:"))
        if (startsWithAws.nonEmpty)
          InvalidArgumentException(
            s"Cannot start tags with 'aws:'. Invalid keys: ${startsWithAws.mkString(", ")}"
          ).asLeft
        else Right(())
      }, {
        val keysTooLong = keys.filter(x => x.isEmpty || x.length > 128)
        if (keysTooLong.nonEmpty)
          InvalidArgumentException(
            s"Tags must be between 1 and 128 characters. Invalid keys: ${keysTooLong.mkString(", ")}"
          ).asLeft
        else Right(())
      }, {
        val invalidKeyCharacters =
          keys.filterNot(x => x.matches("^([\\p{L}\\p{Z}\\p{N}_./=+\\-%@]*)$"))
        if (invalidKeyCharacters.nonEmpty)
          InvalidArgumentException(
            s"Keys contain invalid characters. Invalid keys: ${invalidKeyCharacters.mkString(", ")}"
          ).asLeft
        else Right(())
      }
    ).mapN((_, _, _) => keys)

  def validateRetentionPeriodHours(
      retentionPeriodHours: Int
  ): Response[Int] =
    if (
      retentionPeriodHours < StreamData.minRetentionPeriod.toHours || retentionPeriodHours > StreamData.maxRetentionPeriod.toHours
    )
      InvalidArgumentException(
        s"Retention period hours $retentionPeriodHours must be between ${StreamData.minRetentionPeriod.toHours} and ${StreamData.maxRetentionPeriod.toHours}"
      ).asLeft
    else Right(retentionPeriodHours)

  def validateShardId(
      shardId: String
  ): Response[String] =
    (
      if (!shardId.matches("[a-zA-Z0-9_.-]+"))
        InvalidArgumentException(
          s"Shard ID '$shardId' contains invalid characters"
        ).asLeft
      else Right(()),
      if (shardId.isEmpty || shardId.length() > 128)
        InvalidArgumentException(
          s"Shard ID must be between 1 and 128 characters. Invalid Shard ID: $shardId"
        ).asLeft
      else Right(())
    ).mapN((_, _) => shardId)

  def validateConsumerName(
      consumerName: ConsumerName
  ): Response[ConsumerName] = (
    if (!consumerName.consumerName.matches("[a-zA-Z0-9_.-]+"))
      InvalidArgumentException(
        s"ConsumerName '$consumerName' contains invalid characters"
      ).asLeft
    else Right(()),
    if (
      consumerName.consumerName.isEmpty || consumerName.consumerName
        .length() > 128
    )
      InvalidArgumentException(
        s"ConsumerName must be between 1 and 128 characters. Invalid ConsumerName: $consumerName"
      ).asLeft
    else Right(())
  ).mapN((_, _) => consumerName)

  def findConsumer(
      consumerName: ConsumerName,
      streamData: StreamData
  ): Response[Consumer] =
    streamData.consumers
      .get(consumerName)
      .toRight(
        ResourceNotFoundException(
          s"ConsumerName $consumerName not found on stream ${streamData.streamName}"
        )
      )

  def validateNextToken(
      nextToken: String
  ): Response[String] =
    if (nextToken.isEmpty || nextToken.length() > 1048576)
      InvalidArgumentException(
        s"NextToken length must be between 1 and 1048576"
      ).asLeft
    else Right(nextToken)

  def validateMaxResults(
      maxResults: Int
  ): Response[Int] =
    if (maxResults < 1 || maxResults > 10000)
      InvalidArgumentException(
        s"MaxResults must be between 1 and 10000"
      ).asLeft
    else Right(maxResults)

  def validateLimit(
      limit: Int
  ): Response[Int] =
    if (limit < 1 || limit > 10000)
      InvalidArgumentException(
        s"Limit must be between 1 and 10000"
      ).asLeft
    else Right(limit)

  def validateKeyId(keyId: String): Response[String] =
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
      ).asLeft
    else if (
      !keyId.startsWith("alias/") &&
      !keyId.startsWith("arn:") &&
      Try(UUID.fromString(keyId.takeRight(36))).isFailure
    ) {
      InvalidArgumentException(
        "Received KeyId is not a properly formatted Alias or GUID"
      ).asLeft
    } else if (keyId.isEmpty || keyId.length() > 2048)
      InvalidArgumentException(
        "KeyId must be between 1 and 2048 characters"
      ).asLeft
    else Right(keyId)

  def isKmsEncryptionType(
      encryptionType: EncryptionType
  ): Response[EncryptionType] =
    encryptionType match {
      case EncryptionType.KMS => Right(encryptionType)
      case _ =>
        InvalidArgumentException(
          "KMS is the only valid EncryptionType for this request"
        ).asLeft
    }

  def validateSequenceNumber(
      sequenceNumber: SequenceNumber
  ): Response[SequenceNumber] =
    if (
      SequenceNumberConstant
        .withNameOption(sequenceNumber.value)
        .isEmpty && !sequenceNumber.value.matches("0|([1-9]\\d{0,128})")
    )
      InvalidArgumentException(
        s"SequenceNumber ${sequenceNumber.value} contains invalid characters"
      ).asLeft
    else Right(sequenceNumber)

  def findShard(
      shardId: String,
      stream: StreamData
  ): Response[(Shard, Vector[KinesisRecord])] =
    stream.shards.find { case (shard, _) =>
      shard.shardId.shardId == shardId
    } match {
      case None =>
        ResourceNotFoundException(
          s"Could not find shardId $shardId in stream ${stream.streamName}"
        ).asLeft
      case Some(x) => Right(x)
    }

  def computeShard(
      partitionKey: String,
      explicitHashKey: Option[String],
      stream: StreamData
  ): Response[(Shard, Vector[KinesisRecord])] = {
    (explicitHashKey match {
      case Some(ehk) =>
        val hash = BigInt(ehk)
        if (hash < Shard.minHashKey || hash > Shard.maxHashKey) {
          InvalidArgumentException("ExplicitHashKey is not valid").asLeft
        } else {
          hash.asRight
        }
      case None =>
        Try(
          Md5Utils.computeMD5Hash(partitionKey.getBytes(StandardCharsets.UTF_8))
        ).toEither.bimap(
          e =>
            InvalidArgumentException(
              s"Could not compute MD5 hash, ${e.getMessage}"
            ),
          x => BigInt(1, x)
        )
    }).flatMap { hashInt =>
      stream.shards
        .collectFirst {
          case (shard, data)
              if shard.isOpen && hashInt >= shard.hashKeyRange.startingHashKey && hashInt <= shard.hashKeyRange.endingHashKey =>
            (shard, data)
        } match {
        case None =>
          InvalidArgumentException(
            "Could not find shard for partitionKey"
          ).asLeft
        case Some(x) => Right(x)
      }
    }
  }

  def validateExplicitHashKey(
      explicitHashKey: String
  ): Response[String] =
    if (!explicitHashKey.matches("0|([1-9]\\d{0,38})"))
      InvalidArgumentException(
        "ExplicitHashKey contains invalid characters"
      ).asLeft
    else Right(explicitHashKey)

  def validatePartitionKey(
      partitionKey: String
  ): Response[String] =
    if (partitionKey.isEmpty || partitionKey.length > 256)
      InvalidArgumentException(
        "Partition key must be between 1 and 256 in length"
      ).asLeft
    else Right(partitionKey)

  def isShardOpen(shard: Shard): Response[Shard] =
    if (!shard.isOpen)
      ResourceInUseException(s"Shard ${shard.shardId} is not active").asLeft
    else Right(shard)

  def validateData(
      data: Array[Byte]
  ): Response[Array[Byte]] =
    if (data.length > 1048576)
      InvalidArgumentException("Data object is too large").asLeft
    else Right(data)
}
