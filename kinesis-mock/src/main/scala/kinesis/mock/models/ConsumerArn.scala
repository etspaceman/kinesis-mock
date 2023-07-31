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

package kinesis.mock.models

import scala.util.Try

import java.time.Instant

import cats.Eq
import cats.syntax.all._
import io.circe._

final case class ConsumerArn(
    streamArn: StreamArn,
    consumerName: ConsumerName,
    creationTime: Instant
) {
  val consumerArn =
    s"$streamArn/consumer/$consumerName:${creationTime.getEpochSecond}"
  override def toString: String = consumerArn
}

object ConsumerArn {
  def fromArn(consumerArn: String): Either[String, ConsumerArn] =
    for {
      streamArn <- Try(consumerArn.split("/consumer")(0)).toEither
        .leftMap(e =>
          s"Could not get stream arn part from consumer arn: ${e.getMessage}"
        )
        .flatMap(StreamArn.fromArn)
      consumerName <- Try(consumerArn.split("/")(3)).toEither
        .leftMap(e => s"Could not get consumer name from ARN: ${e.getMessage}")
        .flatMap(x =>
          Try(x.split(":").head).toEither
            .bimap(
              e => s"Could not get consumer name from ARN: ${e.getMessage}",
              ConsumerName.apply
            )
        )
      consumerParts = consumerArn.split(":")
      creationTimestamp <- Try(consumerParts.last).toEither
        .leftMap(_.getMessage)
        .flatMap(x =>
          Try(x.toLong).toEither.leftMap(e =>
            s"Could not convert timestamp to Long: ${e.getMessage}"
          )
        )
        .flatMap(x =>
          Try(Instant.ofEpochSecond(x)).toEither
            .leftMap(e =>
              s"Could not convert timestamp from ARN to Instant: ${e.getMessage}"
            )
        )
    } yield ConsumerArn(streamArn, consumerName, creationTimestamp)

  implicit val consumerArnCirceEncoder: Encoder[ConsumerArn] =
    Encoder[String].contramap(_.consumerArn)
  implicit val consumerArnCirceDecoder: Decoder[ConsumerArn] =
    Decoder[String].emap(ConsumerArn.fromArn)
  implicit val consumerArnCirceKeyEncoder: KeyEncoder[ConsumerArn] =
    KeyEncoder[String].contramap(_.consumerArn)
  implicit val consumerArnCirceKeyDecoder: KeyDecoder[ConsumerArn] =
    KeyDecoder.instance(ConsumerArn.fromArn(_).toOption)
  implicit val consumerArnEq: Eq[ConsumerArn] = (x, y) =>
    x.consumerName === y.consumerName &&
      x.streamArn === y.streamArn &&
      x.creationTime.getEpochSecond() === y.creationTime.getEpochSecond() &&
      x.consumerArn === y.consumerArn
  implicit val consumerArnOrdering: Ordering[ConsumerArn] =
    (x: ConsumerArn, y: ConsumerArn) =>
      Ordering[String].compare(x.consumerArn, y.consumerArn)
}
