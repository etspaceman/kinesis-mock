package kinesis.mock
package api

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class ListStreamConsumersRequest(
    maxResults: Option[Int],
    nextToken: Option[String],
    streamArn: String,
    streamCreationTimestamp: Option[Instant]
) {
  def listStreamConsumers(
      streams: Streams
  ): ValidatedNel[KinesisMockException, ListStreamConsumersResponse] =
    CommonValidations
      .findStreamByArn(streamArn, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamArn(streamArn),
          maxResults match {
            case Some(mr) => CommonValidations.validateMaxResults(mr)
            case None     => Valid(())
          },
          nextToken match {
            case Some(nt) => CommonValidations.validateNextToken(nt)
            case None     => Valid(())
          }
        ).mapN((_, _, _) =>
          nextToken match {
            case Some(nt) => {
              val allConsumers = stream.consumers.values.toList
              val lastConsumerIndex = allConsumers.length - 1
              val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
              val firstIndex = allConsumers.indexWhere(_.consumerName == nt) + 1
              val lastIndex =
                Math.min(firstIndex + limit, lastConsumerIndex) + 1
              val consumers = allConsumers.slice(firstIndex, lastIndex)
              val nextToken =
                if (lastConsumerIndex + 1 == lastIndex) None
                else Some(consumers.last.consumerName)
              ListStreamConsumersResponse(consumers, nextToken)
            }
            case None =>
              val allConsumers = stream.consumers.values.toList
              val lastConsumerIndex = allConsumers.length - 1
              val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
              val lastIndex =
                Math.min(limit, lastConsumerIndex) + 1
              val consumers = allConsumers.take(limit)
              val nextToken =
                if (lastConsumerIndex + 1 == lastIndex) None
                else Some(consumers.last.consumerName)
              ListStreamConsumersResponse(consumers, nextToken)
          }
        )
      )
}

object ListStreamConsumersRequest {
  implicit val listStreamConsumersRequestCirceEncoder
      : Encoder[ListStreamConsumersRequest] =
    Encoder.forProduct4(
      "MaxResults",
      "NextToken",
      "StreamARN",
      "StreamCreationTimestamp"
    )(x => (x.maxResults, x.nextToken, x.streamArn, x.streamCreationTimestamp))

  implicit val listStreamConsumersRequestCirceDecoder
      : Decoder[ListStreamConsumersRequest] = x =>
    for {
      maxResults <- x.downField("MaxResults").as[Option[Int]]
      nextToken <- x.downField("NextToken").as[Option[String]]
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
}
