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

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.instances.circe.*
import kinesis.mock.models.*
import kinesis.mock.validations.CommonValidations

final case class ListStreamConsumersRequest(
    maxResults: Option[Int],
    nextToken: Option[ConsumerName],
    streamArn: StreamArn,
    streamCreationTimestamp: Option[Instant]
):
  def listStreamConsumers(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[ListStreamConsumersResponse]] = streamsRef.get.map { streams =>
    CommonValidations
      .validateStreamArn(streamArn)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamArn, streams)
          .flatMap(stream =>
            (
              maxResults match
                case Some(mr) => CommonValidations.validateMaxResults(mr)
                case None     => Right(())
              ,
              nextToken match
                case Some(nt) =>
                  CommonValidations.validateNextToken(nt.consumerName)
                case None => Right(())
            ).mapN((_, _) =>
              nextToken match
                case Some(nt) =>
                  val allConsumers = stream.consumers.values.toVector
                  val lastConsumerIndex = allConsumers.length - 1
                  val limit =
                    maxResults.map(l => Math.min(l, 100)).getOrElse(100)
                  val firstIndex =
                    allConsumers.indexWhere(_.consumerName == nt) + 1
                  val lastIndex =
                    Math.min(firstIndex + limit, lastConsumerIndex + 1)
                  val consumers = allConsumers.slice(firstIndex, lastIndex)
                  val ntUpdated =
                    if lastConsumerIndex == lastIndex || consumers.isEmpty then
                      None
                    else Some(consumers.last.consumerName)
                  ListStreamConsumersResponse(
                    consumers.map(ConsumerSummary.fromConsumer),
                    ntUpdated
                  )

                case None =>
                  val allConsumers = stream.consumers.values.toVector
                  val lastConsumerIndex = allConsumers.length - 1
                  val limit =
                    maxResults.map(l => Math.min(l, 100)).getOrElse(100)
                  val lastIndex =
                    Math.min(limit, lastConsumerIndex + 1)
                  val consumers = allConsumers.take(limit)
                  val nextToken =
                    if lastConsumerIndex == lastIndex || consumers.isEmpty then
                      None
                    else Some(consumers.last.consumerName)
                  ListStreamConsumersResponse(
                    consumers.map(ConsumerSummary.fromConsumer),
                    nextToken
                  )
            )
          )
      )
  }

object ListStreamConsumersRequest:
  def listStreamConsumersRequestCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ListStreamConsumersRequest] =
    circe.Encoder.forProduct4(
      "MaxResults",
      "NextToken",
      "StreamARN",
      "StreamCreationTimestamp"
    )(x => (x.maxResults, x.nextToken, x.streamArn, x.streamCreationTimestamp))

  def listStreamConsumersRequestCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ListStreamConsumersRequest] = x =>
    for
      maxResults <- x.downField("MaxResults").as[Option[Int]]
      nextToken <- x.downField("NextToken").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[StreamArn]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Option[Instant]]
    yield ListStreamConsumersRequest(
      maxResults,
      nextToken,
      streamArn,
      streamCreationTimestamp
    )

  given listStreamConsumersRequestEncoder: Encoder[ListStreamConsumersRequest] =
    Encoder.instance(
      listStreamConsumersRequestCirceEncoder(instantBigDecimalCirceEncoder),
      listStreamConsumersRequestCirceEncoder(instantLongCirceEncoder)
    )
  given listStreamConsumersRequestDecoder: Decoder[ListStreamConsumersRequest] =
    Decoder.instance(
      listStreamConsumersRequestCirceDecoder(instantBigDecimalCirceDecoder),
      listStreamConsumersRequestCirceDecoder(instantLongCirceDecoder)
    )

  given listStreamConsumersRequestEq: Eq[ListStreamConsumersRequest] =
    (x, y) =>
      x.maxResults == y.maxResults && x.nextToken == y.nextToken && x.streamArn == y.streamArn && x.streamCreationTimestamp
        .map(_.toEpochMilli) == y.streamCreationTimestamp.map(_.toEpochMilli)
