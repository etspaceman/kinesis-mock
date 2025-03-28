/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  given listStreamConsumersResponseEncoder
      : Encoder[ListStreamConsumersResponse] = Encoder.instance(
    listStreamConsumersResponseCirceEncoder(
      Encoder[ConsumerSummary].circeEncoder
    ),
    listStreamConsumersResponseCirceEncoder(
      Encoder[ConsumerSummary].circeCborEncoder
    )
  )
  given listStreamConsumersResponseDecoder
      : Decoder[ListStreamConsumersResponse] = Decoder.instance(
    listStreamConsumersResponseCirceDecoder(
      Decoder[ConsumerSummary].circeDecoder
    ),
    listStreamConsumersResponseCirceDecoder(
      Decoder[ConsumerSummary].circeCborDecoder
    )
  )
  given listStreamConusmerResponseEq: Eq[ListStreamConsumersResponse] =
    (x, y) => x.consumers === y.consumers && x.nextToken == y.nextToken
}
