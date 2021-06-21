package kinesis.mock.api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should delete a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val asActive = streams.findAndUpdateStream(streamName)(x =>
        x.copy(streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams
          .get(streamName)
          .exists(_.streamStatus == StreamStatus.DELETING),
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the stream doesn't exist")(PropF.forAllF {
    streamName: StreamName =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is not set"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val withConsumers = streams.findAndUpdateStream(streamName)(x =>
        x.copy(consumers =
          Map(consumerName -> Consumer.create(x.streamArn, consumerName)),
          streamStatus = StreamStatus.ACTIVE
        )
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
