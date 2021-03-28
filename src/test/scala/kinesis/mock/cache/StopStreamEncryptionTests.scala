package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StopStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should stop stream encryption")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
        keyId <- IO(keyIdGen.one)
        _ <- cache
          .startStreamEncryption(
            StartStreamEncryptionRequest(EncryptionType.KMS, keyId, streamName)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.startStreamEncryptionDuration.plus(50.millis))
        _ <- cache
          .stopStreamEncryption(
            StopStreamEncryptionRequest(EncryptionType.KMS, keyId, streamName)
          )
          .rethrow
        describeReq = DescribeStreamSummaryRequest(streamName)
        checkStream1 <- cache
          .describeStreamSummary(describeReq)
          .rethrow
        _ <- IO.sleep(cacheConfig.stopStreamEncryptionDuration.plus(50.millis))
        checkStream2 <- cache
          .describeStreamSummary(describeReq)
          .rethrow
      } yield assert(
        checkStream1.streamDescriptionSummary.encryptionType.contains(
          EncryptionType.NONE
        ) &&
          checkStream1.streamDescriptionSummary.keyId.isEmpty &&
          checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
      )
  })
}
