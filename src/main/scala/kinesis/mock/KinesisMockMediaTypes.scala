package kinesis.mock

import org.http4s.syntax.literals._
import org.http4s.{MediaRange, MediaType}

object KinesisMockMediaTypes {
  val amazonJson: MediaType = mediaType"application/x-amz-json-1.1"
  val amazonCbor: MediaType = mediaType"application/x-amz-cbor-1.1"
  val validContentTypes: Set[MediaRange] = Set(
    MediaType.application.json,
    amazonJson,
    amazonCbor
  )
}
