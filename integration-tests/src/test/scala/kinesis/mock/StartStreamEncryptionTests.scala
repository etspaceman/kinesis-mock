package kinesis.mock

import scala.concurrent.duration.*

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class StartStreamEncryptionTests extends AwsFunctionalTests:

  fixture.test("It should start stream encryption") { resources =>
    for
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
        resources.cacheConfig.startStreamEncryptionDuration.plus(400.millis)
      )
      res <- describeStreamSummary(resources)
    yield assert(
      res.streamDescriptionSummary().keyId() == keyId &&
        res.streamDescriptionSummary().encryptionType() == EncryptionType.KMS,
      res
    )
  }
