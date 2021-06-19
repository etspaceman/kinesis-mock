package kinesis.mock
package models

import scala.collection.SortedMap

import cats.Eq
import cats.syntax.all._
import io.circe._
import io.circe.derivation._

final case class Streams(streams: SortedMap[StreamName, StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams ++ List(stream.streamName -> stream))
  def findAndUpdateStream(
      streamName: StreamName
  )(f: StreamData => StreamData): Streams =
    copy(
      streams = streams ++
        streams
          .get(streamName)
          .map(stream => (stream.streamName, f(stream)))
          .toMap
    )
  def addStream(
      shardCount: Int,
      streamName: StreamName,
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): Streams = {
    val created = StreamData.create(
      shardCount,
      streamName,
      awsRegion,
      awsAccountId
    )

    copy(streams = streams ++ List(streamName -> created))
  }

  def deleteStream(
      streamName: StreamName
  ): Streams =
    copy(streams =
      streams ++ streams
        .get(streamName)
        .map(stream =>
          streamName -> stream.copy(
            shards = SortedMap.empty,
            streamStatus = StreamStatus.DELETING,
            tags = Tags.empty,
            enhancedMonitoring = List.empty,
            consumers = SortedMap.empty
          )
        )
        .toMap
    )

  def removeStream(streamName: StreamName): Streams =
    copy(streams = streams.filterNot { case (sName, _) => sName == streamName })
}

object Streams {
  val empty: Streams = Streams(SortedMap.empty)
  implicit val streamsCirceEncoder: Encoder[Streams] = deriveEncoder
  implicit val streamsCirceDecoder: Decoder[Streams] = deriveDecoder
  implicit val streamsEq: Eq[Streams] = (x, y) =>
    x.streams.toMap === y.streams.toMap
}
