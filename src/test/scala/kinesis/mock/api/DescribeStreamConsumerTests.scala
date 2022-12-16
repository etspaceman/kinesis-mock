package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe stream consumers by consumerName")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }
      val consumer = updated.streams
        .get(consumerArn.streamArn)
        .flatMap(s => s.consumers.get(consumerArn.consumerName))

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          Some(consumerArn.streamArn)
        )
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(
        res.isRight && res.exists { response =>
          consumer.contains(response.consumerDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should describe stream consumers by consumerArn")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      val consumer = updated.streams
        .get(consumerArn.streamArn)
        .flatMap(s => s.consumers.get(consumerArn.consumerName))

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(Some(consumerArn), None, None)
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(
        res.isRight && res.exists { response =>
          consumer.contains(response.consumerDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer does not exist")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val streamArn =
        streams.streams.get(consumerArn.streamArn).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          streamArn
        )
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject if a consumerName is provided without a streamArn"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          None
        )
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject if a streamArn is provided without a consumerName"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)
      val updated =
        streams.findAndUpdateStream(consumerArn.streamArn) { stream =>
          stream.copy(
            streamStatus = StreamStatus.ACTIVE,
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(stream.streamArn, consumerArn.consumerName)
                .copy(consumerStatus = ConsumerStatus.ACTIVE)
            )
          )
        }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(
          None,
          None,
          Some(consumerArn.streamArn)
        )
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
