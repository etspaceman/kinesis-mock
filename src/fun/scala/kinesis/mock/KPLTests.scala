package kinesis.mock

import cats.effect.Resource
import com.amazonaws.services.kinesis.producer._
import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.regions.Regions
import com.github.f4b6a3.uuid.UuidCreator

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.scalacheck._
import kinesis.mock.syntax.javaFuture._
import java.nio.ByteBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionContext

class KPLTests extends munit.CatsEffectSuite with AwsFunctionalTests {
  implicit val E: ExecutionContextExecutor = ExecutionContext.global

  val kplFixture = ResourceFixture(
    resource.flatMap { resources =>
      Resource
        .make(
          IO(
            new KinesisProducer(
              new KinesisProducerConfiguration()
                .setCredentialsProvider(AwsCreds.LocalCreds)
                .setRegion(Regions.US_EAST_1.getName())
                .setKinesisEndpoint("localhost")
                .setKinesisPort(resources.testConfig.servicePort.toLong)
                .setCloudwatchEndpoint("localhost")
                .setCloudwatchPort(4566L)
                .setVerifyCertificate(false)
            )
          )
        )(x => IO(x.destroy()))
        .map(kpl => KPLResources(resources, kpl))
    },
    (_, resources: KPLResources) => setup(resources.functionalTestResources),
    (resources: KPLResources) => teardown(resources.functionalTestResources)
  )

  kplFixture.test("it should produce records") { resources =>
    for {
      dataRecords <- IO(dataGen.take(5).toList)
      res <- dataRecords.traverse(data =>
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
