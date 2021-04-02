package kinesis.mock
package api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class ListStreamConsumersResponse(
    consumers: List[Consumer],
    nextToken: Option[ConsumerName]
)

object ListStreamConsumersResponse {
  implicit val listStreamConsumersResponseCirceEncoder
      : Encoder[ListStreamConsumersResponse] =
    Encoder.forProduct2("Consumers", "NextToken")(x =>
      (x.consumers, x.nextToken)
    )

  implicit val listStreamConsumersResponseCirceDecoder
      : Decoder[ListStreamConsumersResponse] =
    x =>
      for {
        consumers <- x.downField("Consumers").as[List[Consumer]]
        nextToken <- x.downField("NextToken").as[Option[ConsumerName]]
      } yield ListStreamConsumersResponse(consumers, nextToken)

  implicit val listStreamConusmerResponseEq: Eq[ListStreamConsumersResponse] =
    (x, y) => x.consumers === y.consumers && x.nextToken == y.nextToken
}
