package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe stream consumers by consumerName")(PropF.forAllF {
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
      val consumer = updated.streams
        .get(streamName)
        .flatMap(s => s.consumers.get(consumerName))
      val streamArn = streams.streams.get(streamName).map(_.streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(None, Some(consumerName), streamArn)
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

      val consumer = updated.streams
        .get(streamName)
        .flatMap(s => s.consumers.get(consumerName))

      val consumerArn = consumer.map(_.consumerArn)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DescribeStreamConsumerRequest(consumerArn, None, None)
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
        req = DescribeStreamConsumerRequest(None, Some(consumerName), streamArn)
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
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
        req = DescribeStreamConsumerRequest(None, Some(consumerName), None)
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
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
        req = DescribeStreamConsumerRequest(None, None, streamArn)
        res <- req.describeStreamConsumer(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
