package kinesis.mock

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

import java.nio.ByteBuffer

import cats.effect.{IO, Resource, SyncIO}
import cats.syntax.all._
import software.amazon.awssdk.regions.Region
import software.amazon.kinesis.producer._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class KPLTests extends AwsFunctionalTests {
  implicit val E: ExecutionContextExecutor = ExecutionContext.global

  val kplFixture: SyncIO[FunFixture[KPLResources]] = ResourceFunFixture(
    resource().flatMap { resources =>
      Resource
        .make(
          IO(
            new KinesisProducer(
              new KinesisProducerConfiguration()
                .setCredentialsProvider(AwsCreds.LocalCreds)
                .setRegion(
                  Region.of(resources.awsRegion.entryName).id()
                )
                .setKinesisEndpoint("localhost")
                .setKinesisPort(4567L) // KPL only supports TLS
                .setCloudwatchEndpoint("localhost")
                .setCloudwatchPort(4566L)
                .setStsEndpoint("localhost")
                .setStsPort(4566L)
                .setVerifyCertificate(false)
            )
          )
        )(x => IO(x.flushSync()) *> IO(x.destroy()))
        .map(kpl => KPLResources(resources, kpl))
    }
  )

  kplFixture.test("it should produce records") { resources =>
    for {
      dataRecords <- IO(dataGen.take(20).toVector)
      partitionKey <- Utils.randomUUIDString
      res <- dataRecords.parTraverse(data =>
        resources.kpl
          .addUserRecord(
            new UserRecord(
              resources.functionalTestResources.streamName.streamName,
              partitionKey,
              ByteBuffer.wrap(data)
            )
          )
          .toIO
      )
    } yield assert(res.forall(_.isSuccessful()))
  }
}
