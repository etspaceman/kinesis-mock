package kinesis.mock
package models

import scala.collection.SortedMap

final case class Streams(streams: Map[String, StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams + (stream.streamName -> stream))
  def findAndUpdateStream(streamName: String)(f: StreamData => StreamData) =
    copy(
      streams = streams ++
        streams
          .get(streamName)
          .map(stream => (stream.streamName, f(stream)))
          .toMap
    )
  def addStream(
      shardCount: Int,
      streamName: String,
      awsRegion: AwsRegion,
      awsAccountId: String
  ): Streams =
    copy(streams =
      streams + (streamName -> StreamData.create(
        shardCount,
        streamName,
        awsRegion,
        awsAccountId
      ))
    )

  def deleteStream(streamName: String) =
    copy(streams =
      streams ++ streams
        .get(streamName)
        .map(stream =>
          (streamName -> stream.copy(
            shards = SortedMap.empty,
            streamStatus = StreamStatus.DELETING,
            tags = Map.empty,
            enhancedMonitoring = List.empty,
            consumers = Map.empty
          ))
        )
        .toMap
    )

  def removeStream(streamName: String) =
    copy(streams = streams - streamName)
}

object Streams {
  val empty = Streams(Map.empty)
}
