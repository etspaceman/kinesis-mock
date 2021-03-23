package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamConsumerTests extends munit.ScalaCheckSuite {
  property("It should describe stream consumers by consumerName")(forAll {
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
          consumers = Map(
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

      val req =
        DescribeStreamConsumerRequest(None, Some(consumerName), streamArn)
      val res =
        req.describeStreamConsumer(updated)

      (res.isValid && res.exists { case response =>
        consumer.contains(response.consumerDescription)
      }) :| s"req: $req\nres: $res"
  })

  property("It should describe stream consumers by consumerArn")(forAll {
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
          consumers = Map(
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

      val req = DescribeStreamConsumerRequest(consumerArn, None, None)
      val res =
        req.describeStreamConsumer(updated)

      (res.isValid && res.exists { case response =>
        consumer.contains(response.consumerDescription)
      }) :| s"req: $req\nres: $res"
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
        DescribeStreamConsumerRequest(None, Some(consumerName), streamArn)
      val res =
        req.describeStreamConsumer(streams)

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
          consumers = Map(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }

      val req = DescribeStreamConsumerRequest(None, Some(consumerName), None)
      val res =
        req.describeStreamConsumer(updated)

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
          consumers = Map(
            consumerName -> Consumer
              .create(stream.streamArn, consumerName)
              .copy(consumerStatus = ConsumerStatus.ACTIVE)
          )
        )
      }

      val streamArn = updated.streams.get(streamName).map(_.streamArn)

      val req = DescribeStreamConsumerRequest(None, None, streamArn)
      val res =
        req.describeStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
