package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class RegisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should register stream consumers")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val streamArn = streams.streams(streamName).streamArn

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = RegisterStreamConsumerRequest(consumerName, streamArn)
        res <- req.registerStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isValid && s.streams.get(streamName).exists { stream =>
          stream.consumers.contains(consumerName)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when there are 20 consumers")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(20, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val updated = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = updated.streams(streamName).streamArn

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = RegisterStreamConsumerRequest(consumerName, streamArn)
        res <- req.registerStreamConsumer(streamsRef)
      } yield assert(res.isInvalid, s"req: $req\nres: $res")
  })

  test("It should reject when there are 5 consumers being created")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId,
          consumerName: ConsumerName
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val consumers = SortedMap.from(
          Gen
            .listOfN(5, consumerArbitrary.arbitrary)
            .suchThat(x =>
              x.groupBy(_.consumerName)
                .collect { case (_, y) if y.length > 1 => x }
                .isEmpty
            )
            .map(_.map(c => c.copy(consumerStatus = ConsumerStatus.CREATING)))
            .one
            .map(c => c.consumerName -> c)
        )

        val updated = streams.findAndUpdateStream(streamName)(s =>
          s.copy(consumers = consumers)
        )

        val streamArn = updated.streams(streamName).streamArn

        for {
          streamsRef <- Ref.of[IO, Streams](updated)
          req = RegisterStreamConsumerRequest(consumerName, streamArn)
          res <- req.registerStreamConsumer(streamsRef)
        } yield assert(res.isInvalid, s"req: $req\nres: $res")
    }
  )
}
