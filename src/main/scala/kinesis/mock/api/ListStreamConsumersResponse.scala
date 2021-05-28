package kinesis.mock
package api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class ListStreamConsumersResponse(
    consumers: List[Consumer],
    nextToken: Option[ConsumerName]
)

object ListStreamConsumersResponse {
  def listStreamConsumersResponseCirceEncoder(implicit
      EC: circe.Encoder[Consumer]
  ): circe.Encoder[ListStreamConsumersResponse] =
    circe.Encoder.forProduct2("Consumers", "NextToken")(x =>
      (x.consumers, x.nextToken)
    )

  def listStreamConsumersResponseCirceDecoder(implicit
      DC: circe.Decoder[Consumer]
  ): circe.Decoder[ListStreamConsumersResponse] =
    x =>
      for {
        consumers <- x.downField("Consumers").as[List[Consumer]]
        nextToken <- x.downField("NextToken").as[Option[ConsumerName]]
      } yield ListStreamConsumersResponse(consumers, nextToken)

  implicit val listStreamConsumersResponseEncoder
      : Encoder[ListStreamConsumersResponse] = Encoder.instance(
    listStreamConsumersResponseCirceEncoder(Encoder[Consumer].circeEncoder),
    listStreamConsumersResponseCirceEncoder(Encoder[Consumer].circeCborEncoder)
  )
  implicit val listStreamConsumersResponseDecoder
      : Decoder[ListStreamConsumersResponse] = Decoder.instance(
    listStreamConsumersResponseCirceDecoder(Decoder[Consumer].circeDecoder),
    listStreamConsumersResponseCirceDecoder(Decoder[Consumer].circeCborDecoder)
  )
  implicit val listStreamConusmerResponseEq: Eq[ListStreamConsumersResponse] =
    (x, y) => x.consumers === y.consumers && x.nextToken == y.nextToken
}
