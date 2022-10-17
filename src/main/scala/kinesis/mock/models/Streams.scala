package kinesis.mock
package models

import scala.collection.SortedMap

import cats.Eq
import cats.syntax.all._
import io.circe._

final case class Streams(streams: SortedMap[StreamArn, StreamData]) {
  def updateStream(stream: StreamData): Streams =
    copy(streams = streams ++ Seq(stream.streamArn -> stream))
  def findAndUpdateStream(
      streamArn: StreamArn
  )(f: StreamData => StreamData): Streams =
    streams
      .get(streamArn)
      .map(stream =>
        copy(streams = streams ++ Seq(stream.streamArn -> f(stream)))
      )
      .getOrElse(this)

  def addStream(
      shardCount: Int,
      streamArn: StreamArn
  ): Streams =
    copy(streams =
      streams ++ Seq(
        streamArn -> StreamData.create(shardCount, streamArn)
      )
    )

  def deleteStream(
      streamArn: StreamArn
  ): Streams = streams
    .get(streamArn)
    .map(stream =>
      copy(streams =
        streams ++ Seq(
          streamArn -> stream.copy(
            shards = SortedMap.empty,
            streamStatus = StreamStatus.DELETING,
            tags = Tags.empty,
            enhancedMonitoring = Vector.empty,
            consumers = SortedMap.empty
          )
        )
      )
    )
    .getOrElse(this)

  def removeStream(streamArn: StreamArn): Streams =
    copy(streams = streams.filterNot { case (x, _) => streamArn == x })
}

object Streams {
  val empty: Streams = Streams(SortedMap.empty)
  implicit val streamsCirceEncoder: Encoder[Streams] =
    Encoder.forProduct1("streams")(x => (x.streams))
  implicit val streamsCirceDecoder: Decoder[Streams] = { x =>
    for {
      streams <- x.downField("streams").as[SortedMap[StreamArn, StreamData]]
    } yield Streams(streams)
  }
  implicit val streamsEq: Eq[Streams] = (x, y) =>
    x.streams.toMap === y.streams.toMap
}
