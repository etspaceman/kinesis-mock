package kinesis.mock.api

import scala.collection.SortedMap

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Ref
import cats.effect.std.Semaphore

class DeleteStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should delete a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoresKeys) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val asActive = streams.findAndUpdateStream(streamName)(x =>
        x.copy(streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](asActive)
        semaphores <- shardSemaphoresKeys
          .traverse(key => Semaphore[IO](1).map(s => key -> s))
          .map(_.toMap)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](semaphores)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef, shardSemaphoresRef)
        s <- streamsRef.get
        sems <- shardSemaphoresRef.get
      } yield assert(
        res.isValid && s.streams
          .get(streamName)
          .exists(_.streamStatus == StreamStatus.DELETING) && sems.isEmpty,
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the stream doesn't exist")(PropF.forAllF {
    streamName: StreamName =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](Map.empty)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef, shardSemaphoresRef)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoresKeys) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        semaphores <- shardSemaphoresKeys
          .traverse(key => Semaphore[IO](1).map(s => key -> s))
          .map(_.toMap)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](semaphores)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef, shardSemaphoresRef)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream has consumes and enforceConsumerDeletion is true"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, shardSemaphoresKeys) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val withConsumers = streams.findAndUpdateStream(streamName)(x =>
        x.copy(consumers =
          SortedMap(consumerName -> Consumer.create(x.streamArn, consumerName))
        )
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withConsumers)
        semaphores <- shardSemaphoresKeys
          .traverse(key => Semaphore[IO](1).map(s => key -> s))
          .map(_.toMap)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](semaphores)
        req = DeleteStreamRequest(streamName, None)
        res <- req.deleteStream(streamsRef, shardSemaphoresRef)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
