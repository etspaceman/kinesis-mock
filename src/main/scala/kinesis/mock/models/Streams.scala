package kinesis.mock
package models

import kinesis.mock.api._

final case class Streams(streams: List[Stream]) {
  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): Either[KinesisMockException, Streams] =
    AddTagsToStreamRequest.addTagsToStream(req, this)

  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest
  ): Either[KinesisMockException, Streams] =
    RemoveTagsFromStreamRequest.removeTagsFromStream(req, this)
}
