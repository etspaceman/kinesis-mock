package kinesis.mock
package api

import java.time.Instant

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class ListStreamConsumersRequest(
    maxResults: Option[Int],
    nextToken: Option[ConsumerName],
    streamArn: String,
    streamCreationTimestamp: Option[Instant]
) {
  def listStreamConsumers(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[ListStreamConsumersResponse]] = streamsRef.get.map {
    streams =>
      CommonValidations
        .validateStreamArn(streamArn)
        .andThen(_ =>
          CommonValidations
            .findStreamByArn(streamArn, streams)
            .andThen(stream =>
              (
                maxResults match {
                  case Some(mr) => CommonValidations.validateMaxResults(mr)
                  case None     => Valid(())
                },
                nextToken match {
                  case Some(nt) =>
                    CommonValidations.validateNextToken(nt.consumerName)
                  case None => Valid(())
                }
              ).mapN((_, _) =>
                nextToken match {
                  case Some(nt) =>
                    val allConsumers = stream.consumers.values.toList
                    val lastConsumerIndex = allConsumers.length - 1
                    val limit =
                      maxResults.map(l => Math.min(l, 100)).getOrElse(100)
                    val firstIndex =
                      allConsumers.indexWhere(_.consumerName == nt) + 1
                    val lastIndex =
                      Math.min(firstIndex + limit, lastConsumerIndex + 1)
                    val consumers = allConsumers.slice(firstIndex, lastIndex)
                    val ntUpdated =
                      if (lastConsumerIndex == lastIndex || consumers.isEmpty)
                        None
                      else Some(consumers.last.consumerName)
                    ListStreamConsumersResponse(consumers, ntUpdated)

                  case None =>
                    val allConsumers = stream.consumers.values.toList
                    val lastConsumerIndex = allConsumers.length - 1
                    val limit =
                      maxResults.map(l => Math.min(l, 100)).getOrElse(100)
                    val lastIndex =
                      Math.min(limit, lastConsumerIndex + 1)
                    val consumers = allConsumers.take(limit)
                    val nextToken =
                      if (lastConsumerIndex == lastIndex || consumers.isEmpty)
                        None
                      else Some(consumers.last.consumerName)
                    ListStreamConsumersResponse(consumers, nextToken)
                }
              )
            )
        )
  }
}

object ListStreamConsumersRequest {
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
    for {
      maxResults <- x.downField("MaxResults").as[Option[Int]]
      nextToken <- x.downField("NextToken").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[String]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Option[Instant]]
    } yield ListStreamConsumersRequest(
      maxResults,
      nextToken,
      streamArn,
      streamCreationTimestamp
    )

  implicit val listStreamConsumersRequestEncoder
      : Encoder[ListStreamConsumersRequest] = Encoder.instance(
    listStreamConsumersRequestCirceEncoder(instantBigDecimalCirceEncoder),
    listStreamConsumersRequestCirceEncoder(instantLongCirceEncoder)
  )
  implicit val listStreamConsumersRequestDecoder
      : Decoder[ListStreamConsumersRequest] = Decoder.instance(
    listStreamConsumersRequestCirceDecoder(instantBigDecimalCirceDecoder),
    listStreamConsumersRequestCirceDecoder(instantLongCirceDecoder)
  )

  implicit val listStreamConsumersRequestEq: Eq[ListStreamConsumersRequest] =
    (x, y) =>
      x.maxResults == y.maxResults && x.nextToken == y.nextToken && x.streamArn == y.streamArn && x.streamCreationTimestamp
        .map(_.toEpochMilli) == y.streamCreationTimestamp.map(_.toEpochMilli)
}
