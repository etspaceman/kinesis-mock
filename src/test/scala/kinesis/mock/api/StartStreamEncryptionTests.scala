package kinesis.mock.api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StartStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should start stream encryption")(PropF.forAllF {
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

      val keyId = keyIdGen.one

      for {
        streamsRef <- Ref.of[IO, Streams](asActive)
        req = StartStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          streamName
        )
        res <- req.startStreamEncryption(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams
          .get(streamName)
          .exists { s =>
            s.keyId.contains(keyId) &&
            s.encryptionType == EncryptionType.KMS &&
            s.streamStatus == StreamStatus.UPDATING
          },
        s"req: $req\nres: $res\nstreams: $asActive"
      )
  })

  test("It should reject when the KMS encryption type is not used")(
    PropF.forAllF {
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

        val keyId = keyIdGen.one

        for {
          streamsRef <- Ref.of[IO, Streams](asActive)
          req = StartStreamEncryptionRequest(
            EncryptionType.NONE,
            keyId,
            streamName
          )
          res <- req.startStreamEncryption(streamsRef)
        } yield assert(
          res.isLeft,
          s"req: $req\nres: $res\nstreams: $asActive"
        )
    }
  )

  test("It should reject when the stream is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val keyId = keyIdGen.one

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = StartStreamEncryptionRequest(
          EncryptionType.KMS,
          keyId,
          streamName
        )
        res <- req.startStreamEncryption(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res\nstreams: $streams")
  })
}
