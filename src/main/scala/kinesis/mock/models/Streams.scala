package kinesis.mock
package models

import cats.Eq
import cats.syntax.all._
import io.circe._
import io.circe.derivation._

final case class Streams(streams: Map[StreamName, StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams + (stream.streamName -> stream))
  def findAndUpdateStream(
      streamName: StreamName
  )(f: StreamData => StreamData): Streams =
    streams
      .get(streamName)
      .map(stream => copy(streams = streams + (stream.streamName -> f(stream))))
      .getOrElse(this)

  def addStream(
      shardCount: Int,
      streamName: StreamName,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): Streams =
    copy(streams =
      streams + (streamName -> StreamData.create(
        shardCount,
        streamName,
        awsRegion,
        awsAccountId
      ))
    )

  def deleteStream(
      streamName: StreamName
  ): Streams = streams
    .get(streamName)
    .map(stream =>
      copy(streams =
        streams + (streamName -> stream.copy(
          shards = Map.empty,
          streamStatus = StreamStatus.DELETING,
          tags = Tags.empty,
          enhancedMonitoring = Vector.empty,
          consumers = Map.empty
        ))
      )
    )
    .getOrElse(this)

  def removeStream(streamName: StreamName): Streams =
    copy(streams = streams - streamName)
}

object Streams {
  val empty: Streams = Streams(Map.empty)
  implicit val streamsCirceEncoder: Encoder[Streams] = deriveEncoder
  implicit val streamsCirceDecoder: Decoder[Streams] = deriveDecoder
  implicit val streamsEq: Eq[Streams] = (x, y) =>
    x.streams.toMap === y.streams.toMap
}
