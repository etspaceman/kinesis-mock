package kinesis.mock

import scala.concurrent.duration._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class StartStreamEncryptionTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should start stream encryption") { resources =>
    for {
      keyId <- IO(keyIdGen.one)
      _ <- resources.kinesisClient
        .startStreamEncryption(
          StartStreamEncryptionRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .keyId(keyId)
            .encryptionType(EncryptionType.KMS)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.startStreamEncryptionDuration.plus(200.millis)
      )
      res <- describeStreamSummary(resources)
    } yield assert(
      res.streamDescriptionSummary().keyId() == keyId &&
        res.streamDescriptionSummary().encryptionType() == EncryptionType.KMS,
      res
    )
  }
}
