package kinesis.mock
package api

import io.circe._

import kinesis.mock.models._
import cats.kernel.Eq

final case class ListStreamConsumersResponse(
    consumers: List[Consumer],
    nextToken: Option[String]
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
        nextToken <- x.downField("NextToken").as[Option[String]]
      } yield ListStreamConsumersResponse(consumers, nextToken)

  implicit val listStreamConusmerResponseEq: Eq[ListStreamConsumersResponse] =
    Eq.fromUniversalEquals
}
