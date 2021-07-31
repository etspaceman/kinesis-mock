package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Ref

class DeregisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should deregister stream consumers by consumerName")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val updated = streams.findAndUpdateStream(streamName) { stream =>
        stream.copy(
          streamStatus = StreamStatus.ACTIVE,
          consumers = SortedMap(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }
      val streamArn = streams.streams.get(streamName).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(
          None,
          Some(consumerName),
          streamArn
        )
        res <- req.deregisterStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.consumers
            .get(consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should deregister stream consumers by consumerArn")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val updated = streams.findAndUpdateStream(streamName) { stream =>
        stream.copy(
          streamStatus = StreamStatus.ACTIVE,
          consumers = SortedMap(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }

      val consumerArn = updated.streams
        .get(streamName)
        .flatMap(_.consumers.get(consumerName).map(_.consumerArn))

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(consumerArn, None, None)
        res <- req.deregisterStreamConsumer(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.consumers
            .get(consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val updated = streams.findAndUpdateStream(streamName) { stream =>
        stream.copy(
          streamStatus = StreamStatus.ACTIVE,
          consumers = SortedMap(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
          )
        )
      }

      val consumerArn = updated.streams
        .get(streamName)
        .flatMap(_.consumers.get(consumerName).map(_.consumerArn))

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(consumerArn, None, None)
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if consumer does not exist")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val streamArn = streams.streams.get(streamName).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeregisterStreamConsumerRequest(
          None,
          Some(consumerName),
          streamArn
        )
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test(
    "It should reject if a consumerName is provided without a streamArn"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val updated = streams.findAndUpdateStream(streamName) { stream =>
        stream.copy(
          streamStatus = StreamStatus.ACTIVE,
          consumers = SortedMap(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(None, Some(consumerName), None)
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test(
    "It should reject if a streamArn is provided without a consumerName"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val updated = streams.findAndUpdateStream(streamName) { stream =>
        stream.copy(
          streamStatus = StreamStatus.ACTIVE,
          consumers = SortedMap(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }

      val streamArn = updated.streams.get(streamName).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DeregisterStreamConsumerRequest(None, None, streamArn)
        res <- req.deregisterStreamConsumer(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })
}
