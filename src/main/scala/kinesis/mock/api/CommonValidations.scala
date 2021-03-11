package kinesis.mock
package api

import cats.syntax.all._

import kinesis.mock.models._

object CommonValidations {
  def validateStreamName(
      streamName: String
  ): Either[KinesisMockException, String] = for {
    _ <-
      if (!streamName.matches("[a-zA-Z0-9_.-]+"))
        Left(
          InvalidArgumentException(
            s"Stream Name '$streamName' contains invalid characters"
          )
        )
      else Right(())
    _ <-
      if (streamName.isEmpty || streamName.length() > 128)
        Left(
          InvalidArgumentException(
            s"Stream name must be between 1 and 128 characters. Invalid stream name: $streamName"
          )
        )
      else Right(())
  } yield streamName

  def findStream(
      streamName: String,
      streams: Streams
  ): Either[KinesisMockException, StreamData] = Either.fromOption(
    streams.streams.find(_.name == streamName),
    ResourceNotFoundException(s"Stream name ${streamName} not found")
  )

  def isStreamActive(
      streamName: String,
      streams: Streams
  ): Either[KinesisMockException, String] =
    if (
      streams.streams
        .find(_.name == streamName)
        .exists(_.status != StreamStatus.ACTIVE)
    )
      Left(
        ResourceInUseException(s"Stream ${streamName} is not currently ACTIVE.")
      )
    else Right(streamName)

  def validateShardLimit(
      shardCountToAdd: Int,
      streams: Streams,
      shardLimit: Int
  ): Either[KinesisMockException, Int] =
    if (streams.streams.map(_.data.size).sum + shardCountToAdd > shardLimit)
      Left(
        LimitExceededException(
          s"Request would exceed the shard limit of $shardLimit"
        )
      )
    else Right(shardCountToAdd)

  def validateTagKeys(
      keys: Iterable[String]
  ): Either[KinesisMockException, Iterable[String]] = for {
    _ <- {
      val startsWithAws = keys.filter(_.startsWith("aws:"))
      if (startsWithAws.nonEmpty)
        Left(
          InvalidArgumentException(
            s"Cannot start tags with 'aws:'. Invalid keys: ${startsWithAws.mkString(", ")}"
          )
        )
      else Right(())
    }
    _ <- {
      val keysTooLong =
        keys.filter(x => x.isEmpty() || x.length > 128)
      if (keysTooLong.nonEmpty)
        Left(
          InvalidArgumentException(
            s"Tags must be between 1 and 128 characters. Invalid keys: ${keysTooLong.mkString(", ")}"
          )
        )
      else Right(())
    }
    _ <- {
      val invalidKeyCharacters =
        keys.filterNot(x => x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$"))
      if (invalidKeyCharacters.nonEmpty)
        Left(
          InvalidArgumentException(
            s"Keys contain invalid characters. Invalid keys: ${invalidKeyCharacters.mkString(", ")}"
          )
        )
      else Right(())
    }
  } yield keys
}
