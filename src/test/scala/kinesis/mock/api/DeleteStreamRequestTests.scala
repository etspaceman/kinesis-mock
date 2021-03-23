package kinesis.mock.api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeleteStreamdRequestTests extends munit.ScalaCheckSuite {
  property("It should delete a stream")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val asActive = streams.findAndUpdateStream(streamName)(x =>
        x.copy(streamStatus = StreamStatus.ACTIVE)
      )

      val req = DeleteStreamRequest(streamName, None)
      val res = req.deleteStream(asActive)

      (res.isValid && res.exists { case (s, _) =>
        s.streams
          .get(streamName)
          .exists(_.streamStatus == StreamStatus.DELETING)
      }) :| s"req: $req\nres: $res\nstreams: $asActive"
  })

  property("It should reject when the stream doesn't exist")(forAll {
    streamName: StreamName =>
      val streams = Streams.empty

      val req = DeleteStreamRequest(streamName, None)
      val res = req.deleteStream(streams)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })

  property("It should reject when the stream is not active")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val req = DeleteStreamRequest(streamName, None)
      val res = req.deleteStream(streams)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })

  property(
    "It should reject when the stream has consumes and enforceConsumerDeletion is true"
  )(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val withConsumers = streams.findAndUpdateStream(streamName)(x =>
        x.copy(consumers =
          Map(consumerName -> Consumer.create(x.streamArn, consumerName))
        )
      )

      val req = DeleteStreamRequest(streamName, Some(true))
      val res = req.deleteStream(withConsumers)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })
}
