package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeregisterStreamConsumerTests extends munit.ScalaCheckSuite {
  property("It should deregister stream consumers by consumerName")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
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

      val req =
        DeregisterStreamConsumerRequest(None, Some(consumerName), streamArn)
      val res =
        req.deregisterStreamConsumer(updated)

      (res.isValid && res.exists { case (s, _) =>
        s.streams.get(streamName).exists { stream =>
          stream.consumers
            .get(consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        }
      }) :| s"req: $req\nres: $res"
  })

  property("It should deregister stream consumers by consumerArn")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
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

      val req = DeregisterStreamConsumerRequest(consumerArn, None, None)
      val res =
        req.deregisterStreamConsumer(updated)

      (res.isValid && res.exists { case (s, _) =>
        s.streams.get(streamName).exists { stream =>
          stream.consumers
            .get(consumerName)
            .exists(_.consumerStatus == ConsumerStatus.DELETING)
        }
      }) :| s"req: $req\nres: $res"
  })

  property("It should reject if consumer is not active")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
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

      val req = DeregisterStreamConsumerRequest(consumerArn, None, None)
      val res =
        req.deregisterStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })

  property("It should reject if consumer does not exist")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val streamArn = streams.streams.get(streamName).map(_.streamArn)

      val req =
        DeregisterStreamConsumerRequest(None, Some(consumerName), streamArn)
      val res =
        req.deregisterStreamConsumer(streams)

      res.isInvalid :| s"req: $req\nres: $res"
  })

  property(
    "It should reject if a consumerName is provided without a streamArn"
  )(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
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

      val req = DeregisterStreamConsumerRequest(None, Some(consumerName), None)
      val res =
        req.deregisterStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })

  property(
    "It should reject if a streamArn is provided without a consumerName"
  )(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
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

      val req = DeregisterStreamConsumerRequest(None, None, streamArn)
      val res =
        req.deregisterStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
