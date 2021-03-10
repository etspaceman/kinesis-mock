package kinesis.mock
package models

final case class Streams(streams: List[StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams.filterNot(_.name == stream.name) :+ stream)
  def findAndUpdateStream(streamName: String)(f: StreamData => StreamData) =
    copy(
      streams = streams.filterNot(_.name == streamName) ++
        streams.find(_.name == streamName).map(f).toList
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
}

object Streams {
  val empty = Streams(List.empty)
}
