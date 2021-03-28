package kinesis.mock.api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StopStreamEncryptionTests extends munit.ScalaCheckSuite {
  property("It should stop stream encryption")(forAll {
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

      val req =
        StopStreamEncryptionRequest(EncryptionType.KMS, keyId, streamName)
      val res = req.stopStreamEncryption(asActive)

      (res.isValid && res.exists { s =>
        s.streams
          .get(streamName)
          .exists { s =>
            s.keyId.isEmpty &&
            s.encryptionType == EncryptionType.NONE &&
            s.streamStatus == StreamStatus.UPDATING
          }
      }) :| s"req: $req\nres: $res\nstreams: $asActive"
  })

  property("It should reject when the KMS encryption type is not used")(forAll {
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

      val req =
        StopStreamEncryptionRequest(EncryptionType.NONE, keyId, streamName)
      val res = req.stopStreamEncryption(asActive)

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

      val keyId = keyIdGen.one

      val req =
        StopStreamEncryptionRequest(EncryptionType.KMS, keyId, streamName)
      val res = req.stopStreamEncryption(streams)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })
}
