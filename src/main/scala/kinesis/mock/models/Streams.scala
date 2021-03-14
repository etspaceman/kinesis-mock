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
  ): (Streams, List[ShardSemaphoresKey]) = {
    val created = StreamData.create(
      shardCount,
      streamName,
      awsRegion,
      awsAccountId
    )

    (copy(streams = streams + (streamName -> created._1)), created._2)
  }

  def deleteStream(streamName: String): (Streams, List[ShardSemaphoresKey]) =
    (
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
      ),
      streams
        .get(streamName)
        .toList
        .flatMap(x =>
          x.shards.keys.toList.map(shard =>
            ShardSemaphoresKey(x.streamName, shard)
          )
        )
    )

  def removeStream(streamName: String) =
    copy(streams = streams - streamName)
}

object Streams {
  val empty = Streams(Map.empty)
}
