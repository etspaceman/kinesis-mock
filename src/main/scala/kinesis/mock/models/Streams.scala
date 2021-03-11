package kinesis.mock
package models

import scala.collection.SortedMap

final case class Streams(streams: List[StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams =
      streams.filterNot(_.streamName == stream.streamName) :+ stream
    )
  def findAndUpdateStream(streamName: String)(f: StreamData => StreamData) =
    copy(
      streams = streams.filterNot(_.streamName == streamName) ++
        streams.find(_.streamName == streamName).map(f).toList
    )
  def addStream(
      shardCount: Int,
      streamName: String,
      awsRegion: AwsRegion,
      awsAccountId: String
  ): Streams =
    copy(streams =
      streams :+ StreamData.create(
        shardCount,
        streamName,
        awsRegion,
        awsAccountId
      )
    )

  def deleteStream(streamName: String) =
    copy(streams =
      streams.filterNot(_.streamName == streamName) ++ streams
        .find(_.streamName == streamName)
        .map(stream =>
          stream.copy(
            shards = SortedMap.empty,
            streamStatus = StreamStatus.DELETING,
            tags = Map.empty,
            enhancedMonitoring = List.empty,
            consumers = List.empty
          )
        )
    )

  def removeStream(streamName: String) =
    copy(streams = streams.filterNot(_.streamName == streamName))

}

object Streams {
  val empty = Streams(List.empty)
}
