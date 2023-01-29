package kinesis.mock.api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should delete a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val asActive = streams.findAndUpdateStream(streamArn)(x =>
        x.copy(streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req.deleteStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams
          .get(streamArn)
          .exists(_.streamStatus == StreamStatus.DELETING),
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the stream doesn't exist")(PropF.forAllF {
    streamArn: StreamArn =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req
          .deleteStream(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DeleteStreamRequest(None, Some(streamArn), None)
        res <- req
          .deleteStream(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is not set"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val withConsumers =
        streams.findAndUpdateStream(consumerArn.streamArn)(x =>
          x.copy(
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(x.streamArn, consumerArn.consumerName)
            ),
            streamStatus = StreamStatus.ACTIVE
          )
        )

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = DeleteStreamRequest(None, Some(consumerArn.streamArn), None)
        res <- req.deleteStream(
          streamsRef,
          consumerArn.streamArn.awsRegion,
          consumerArn.streamArn.awsAccountId
        )
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is false"
  )(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      val streams =
        Streams.empty.addStream(1, consumerArn.streamArn, None)

      val withConsumers =
        streams.findAndUpdateStream(consumerArn.streamArn)(x =>
          x.copy(
            consumers = SortedMap(
              consumerArn.consumerName -> Consumer
                .create(x.streamArn, consumerArn.consumerName)
            ),
            streamStatus = StreamStatus.ACTIVE
          )
        )

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        req = DeleteStreamRequest(
          None,
          Some(consumerArn.streamArn),
          Some(false)
        )
        res <- req.deleteStream(
          streamsRef,
          consumerArn.streamArn.awsRegion,
          consumerArn.streamArn.awsAccountId
        )
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
