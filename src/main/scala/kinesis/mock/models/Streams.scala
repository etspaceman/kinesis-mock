package kinesis.mock
package models

final case class Streams(streams: List[Stream])

object Streams {
  val empty = Streams(List.empty)
}
