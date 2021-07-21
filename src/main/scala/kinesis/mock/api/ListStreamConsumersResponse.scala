package kinesis.mock
package api

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class ListStreamConsumersResponse(
    consumers: Vector[ConsumerSummary],
    nextToken: Option[ConsumerName]
)

object ListStreamConsumersResponse {
  def listStreamConsumersResponseCirceEncoder(implicit
      EC: circe.Encoder[ConsumerSummary]
  ): circe.Encoder[ListStreamConsumersResponse] =
    circe.Encoder.forProduct2("Consumers", "NextToken")(x =>
      (x.consumers, x.nextToken)
    )

  def listStreamConsumersResponseCirceDecoder(implicit
      DC: circe.Decoder[ConsumerSummary]
  ): circe.Decoder[ListStreamConsumersResponse] =
    x =>
      for {
        consumers <- x.downField("Consumers").as[Vector[ConsumerSummary]]
        nextToken <- x.downField("NextToken").as[Option[ConsumerName]]
      } yield ListStreamConsumersResponse(consumers, nextToken)

  implicit val listStreamConsumersResponseEncoder
      : Encoder[ListStreamConsumersResponse] = Encoder.instance(
    listStreamConsumersResponseCirceEncoder(
      Encoder[ConsumerSummary].circeEncoder
    ),
    listStreamConsumersResponseCirceEncoder(
      Encoder[ConsumerSummary].circeCborEncoder
    )
  )
  implicit val listStreamConsumersResponseDecoder
      : Decoder[ListStreamConsumersResponse] = Decoder.instance(
    listStreamConsumersResponseCirceDecoder(
      Decoder[ConsumerSummary].circeDecoder
    ),
    listStreamConsumersResponseCirceDecoder(
      Decoder[ConsumerSummary].circeCborDecoder
    )
  )
  implicit val listStreamConusmerResponseEq: Eq[ListStreamConsumersResponse] =
    (x, y) => x.consumers === y.consumers && x.nextToken == y.nextToken
}
