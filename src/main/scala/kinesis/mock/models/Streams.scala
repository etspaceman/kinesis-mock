package kinesis.mock
package models

import scala.collection.SortedMap

final case class Streams(streams: SortedMap[StreamName, StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams ++ List(stream.streamName -> stream))
  def findAndUpdateStream(streamName: StreamName)(f: StreamData => StreamData) =
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
  ): (Streams, List[ShardSemaphoresKey]) = {
    val created = StreamData.create(
      shardCount,
      streamName,
      awsRegion,
      awsAccountId
    )

    (copy(streams = streams ++ List(streamName -> created._1)), created._2)
  }

  def deleteStream(
      streamName: StreamName
  ): (Streams, List[ShardSemaphoresKey]) =
    (
      copy(streams =
        streams ++ streams
          .get(streamName)
          .map(stream =>
            (streamName -> stream.copy(
              shards = SortedMap.empty,
              streamStatus = StreamStatus.DELETING,
              tags = Tags.empty,
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

  def removeStream(streamName: StreamName) =
    copy(streams = streams.filterNot { case (sName, _) => sName == streamName })
}

object Streams {
  val empty = Streams(SortedMap.empty)
}
