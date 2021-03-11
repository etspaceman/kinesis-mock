package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.syntax.all._

import kinesis.mock.models._

object CommonValidations {
  def validateStreamName(
      streamName: String
  ): ValidatedNel[KinesisMockException, String] =
    (
      if (!streamName.matches("[a-zA-Z0-9_.-]+"))
        InvalidArgumentException(
          s"Stream Name '$streamName' contains invalid characters"
        ).invalidNel
      else Valid(()),
      if (streamName.isEmpty || streamName.length() > 128)
        InvalidArgumentException(
          s"Stream name must be between 1 and 128 characters. Invalid stream name: $streamName"
        ).invalidNel
      else Valid(())
    ).mapN((_, _) => streamName)

  def validateStreamArn(
      streamArn: String
  ): ValidatedNel[KinesisMockException, String] = (
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
      streamName: String,
      streams: Streams
  ): ValidatedNel[KinesisMockException, StreamData] =
    streams.streams
      .get(streamName)
      .toValidNel(
        ResourceNotFoundException(s"Stream name ${streamName} not found")
      )

  def findStreamByArn(
      streamArn: String,
      streams: Streams
  ): ValidatedNel[KinesisMockException, StreamData] = streams.streams.values
    .find(_.streamArn == streamArn)
    .toValidNel(ResourceNotFoundException(s"StreamARN ${streamArn} not found"))

  def findStreamByConsumerArn(
      consumerArn: String,
      streams: Streams
  ): ValidatedNel[KinesisMockException, (Consumer, StreamData)] =
    streams.streams.values
      .find(_.consumers.values.exists(_.consumerArn == consumerArn))
      .flatMap(stream =>
        stream.consumers.values
          .find(_.consumerArn == consumerArn)
          .map(consumer => (consumer, stream))
      )
      .toValidNel(
        ResourceNotFoundException(s"ConsumerARN ${consumerArn} not found")
      )

  def isStreamActive(
      streamName: String,
      streams: Streams
  ): ValidatedNel[KinesisMockException, String] =
    if (
      streams.streams
        .get(streamName)
        .exists(_.streamStatus != StreamStatus.ACTIVE)
    )
      ResourceInUseException(
        s"Stream ${streamName} is not currently ACTIVE."
      ).invalidNel
    else streamName.valid

  def validateShardLimit(
      shardCountToAdd: Int,
      streams: Streams,
      shardLimit: Int
  ): ValidatedNel[KinesisMockException, Int] =
    if (
      streams.streams.values.map(_.shards.keys.filter(_.isOpen).size).sum +
        shardCountToAdd > shardLimit
    )
      LimitExceededException(
        s"Request would exceed the shard limit of $shardLimit"
      ).invalidNel
    else shardCountToAdd.valid

  def validateShardCount(
      shardCount: Int
  ): ValidatedNel[KinesisMockException, Int] =
    if (shardCount < 1 || shardCount > 1000)
      LimitExceededException(
        s"The shard count must be between 1 and 1000"
      ).invalidNel
    else shardCount.valid

  def validateTagKeys(
      keys: Iterable[String]
  ): ValidatedNel[KinesisMockException, Iterable[String]] =
    (
      {
        val startsWithAws = keys.filter(_.startsWith("aws:"))
        if (startsWithAws.nonEmpty)
          InvalidArgumentException(
            s"Cannot start tags with 'aws:'. Invalid keys: ${startsWithAws.mkString(", ")}"
          ).invalidNel
        else Valid(())
      }, {
        val keysTooLong = keys.filter(x => x.isEmpty() || x.length > 128)
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
  ): ValidatedNel[KinesisMockException, Int] =
    if (
      retentionPeriodHours < StreamData.minRetentionPeriod.toHours || retentionPeriodHours > StreamData.maxRetentionPeriod.toHours
    )
      InvalidArgumentException(
        s"Retention period hours $retentionPeriodHours must be between ${StreamData.minRetentionPeriod.toHours} and ${StreamData.maxRetentionPeriod.toHours}"
      ).invalidNel
    else Valid(retentionPeriodHours)

  def validateShardId(
      shardId: String
  ): ValidatedNel[KinesisMockException, String] =
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
      consumerName: String
  ): ValidatedNel[KinesisMockException, String] = (
    if (!consumerName.matches("[a-zA-Z0-9_.-]+"))
      InvalidArgumentException(
        s"ConsumerName '$consumerName' contains invalid characters"
      ).invalidNel
    else Valid(()),
    if (consumerName.isEmpty || consumerName.length() > 128)
      InvalidArgumentException(
        s"ConsumerName must be between 1 and 128 characters. Invalid ConsumerName: $consumerName"
      ).invalidNel
    else Valid(())
  ).mapN((_, _) => consumerName)

  def findConsumer(
      consumerName: String,
      streamData: StreamData
  ): ValidatedNel[KinesisMockException, Consumer] =
    streamData.consumers
      .get(consumerName)
      .toValidNel(
        ResourceNotFoundException(
          s"ConsumerName $consumerName not found on stream ${streamData.streamName}"
        )
      )
}
