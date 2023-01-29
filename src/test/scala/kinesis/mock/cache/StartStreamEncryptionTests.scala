package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class StartStreamEncryptionTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should start stream encryption")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(Some(1), None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        keyId <- IO(keyIdGen.one)
        _ <- cache
          .startStreamEncryption(
            StartStreamEncryptionRequest(
              EncryptionType.KMS,
              keyId,
              Some(streamName),
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        describeReq = DescribeStreamSummaryRequest(Some(streamName), None)
        checkStream1 <- cache
          .describeStreamSummary(describeReq, context, false, Some(awsRegion))
          .rethrow
        _ <- IO.sleep(
          cacheConfig.startStreamEncryptionDuration.plus(400.millis)
        )
        checkStream2 <- cache
          .describeStreamSummary(describeReq, context, false, Some(awsRegion))
          .rethrow
      } yield assert(
        checkStream1.streamDescriptionSummary.encryptionType.contains(
          EncryptionType.KMS
        ) &&
          checkStream1.streamDescriptionSummary.keyId.contains(keyId) &&
          checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
      )
  })
}
