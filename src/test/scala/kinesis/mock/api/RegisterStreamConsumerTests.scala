package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
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
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = RegisterStreamConsumerRequest(
          consumerArn.consumerName,
          consumerArn.streamArn
        )
        res <- req.registerStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(consumerArn.streamArn).exists { stream =>
          stream.consumers.contains(consumerArn.consumerName)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when there are 20 consumers")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn)

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

      val updated = streams.findAndUpdateStream(consumerArn.streamArn)(s =>
        s.copy(consumers = consumers)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = RegisterStreamConsumerRequest(
          consumerArn.consumerName,
          consumerArn.streamArn
        )
        res <- req.registerStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject when there are 5 consumers being created")(
    PropF.forAllF {
      (
        consumerArn: ConsumerArn
      ) =>
        val streams =
          Streams.empty.addStream(1, consumerArn.streamArn)

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

        val updated = streams.findAndUpdateStream(consumerArn.streamArn)(s =>
          s.copy(consumers = consumers)
        )

        for {
          streamsRef <- Ref.of[IO, Streams](updated)
          req = RegisterStreamConsumerRequest(
            consumerArn.consumerName,
            consumerArn.streamArn
          )
          res <- req.registerStreamConsumer(streamsRef)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
}
