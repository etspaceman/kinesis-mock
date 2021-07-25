package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list consumers")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(5, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val withConsumers = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = withConsumers.streams(streamName).streamArn

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = ListStreamConsumersRequest(None, None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)

      } yield assert(
        res.isRight && res.exists { response =>
          consumers.values.toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should list consumers when consumers are empty")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val streamArn = streams.streams(streamName).streamArn

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamConsumersRequest(None, None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)

      } yield assert(
        res.isRight && res.exists(_.consumers.isEmpty),
        s"req: $req\nres: $res"
      )
  })

  test("It should paginate properly")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(10, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val withConsumers = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = withConsumers.streams(streamName).streamArn

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = ListStreamConsumersRequest(Some(5), None, streamArn, None)
        res <- req.listStreamConsumers(streamsRef)
        paginatedRes <- res
          .traverse(result =>
            ListStreamConsumersRequest(
              Some(5),
              result.nextToken,
              streamArn,
              None
            ).listStreamConsumers(streamsRef)
          )
          .map(_.flatMap(identity))

      } yield assert(
        res.isRight && paginatedRes.isRight && res.exists { response =>
          consumers.values
            .take(5)
            .toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        } && paginatedRes.exists { response =>
          consumers.values
            .takeRight(5)
            .toVector
            .map(ConsumerSummary.fromConsumer) === response.consumers
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.consumers.length)}\n" +
          s"paginatedResCount: ${paginatedRes.map(_.consumers.length)}}"
      )
  })

}
