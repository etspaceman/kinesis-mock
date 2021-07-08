package kinesis.mock

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

import java.nio.ByteBuffer

import cats.effect.{IO, Resource, SyncIO}
import cats.syntax.all._
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.producer._
import com.github.f4b6a3.uuid.UuidCreator

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class KPLTests extends KinesisMockSuite with AwsFunctionalTests {
  implicit val E: ExecutionContextExecutor = ExecutionContext.global

  val kplFixture: SyncIO[FunFixture[KPLResources]] = ResourceFixture(
    resource.flatMap { resources =>
      Resource
        .make(
          IO(
            new KinesisProducer(
              new KinesisProducerConfiguration()
                .setCredentialsProvider(AwsCreds.LocalCreds)
                .setRegion(Regions.US_EAST_1.getName)
                .setKinesisEndpoint("localhost")
                .setKinesisPort(4567L) // KPL only supports TLS
                .setCloudwatchEndpoint("localhost")
                .setCloudwatchPort(4566L)
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
      res <- dataRecords.parTraverse(data =>
        resources.kpl
          .addUserRecord(
            new UserRecord(
              resources.functionalTestResources.streamName.streamName,
              UuidCreator.toString(UuidCreator.getTimeBased()),
              ByteBuffer.wrap(data)
            )
          )
          .toIO
      )
    } yield assert(res.forall(_.isSuccessful()))
  }
}
