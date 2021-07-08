package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import java.net.URI
import java.time.Instant
import java.util.Date

import cats.effect.std.{Queue, Supervisor}
import cats.effect.{Deferred, IO, Resource}
import cats.syntax.all._
import com.github.f4b6a3.uuid.UuidCreator
import retry._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.kinesis.checkpoint.CheckpointConfig
import software.amazon.kinesis.common._
import software.amazon.kinesis.coordinator.{CoordinatorConfig, Scheduler}
import software.amazon.kinesis.leases.LeaseManagementConfig
import software.amazon.kinesis.lifecycle.LifecycleConfig
import software.amazon.kinesis.metrics.MetricsConfig
import software.amazon.kinesis.processor.ProcessorConfig
import software.amazon.kinesis.retrieval.polling.PollingConfig
import software.amazon.kinesis.retrieval.{KinesisClientRecord, RetrievalConfig}

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.id._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class KCLTests extends munit.CatsEffectSuite with AwsFunctionalTests {
  override val munitTimeout = 2.minutes

  def kclFixture(initialPosition: InitialPositionInStreamExtended) =
    ResourceFixture(
      resource.flatMap { resources =>
        for {
          cloudwatchClient <- Resource.fromAutoCloseable(
            IO(
              CloudWatchAsyncClient
                .builder()
                .httpClient(nettyClient)
                .region(Region.US_EAST_1)
                .credentialsProvider(AwsCreds.LocalCreds)
                .endpointOverride(
                  URI.create(s"https://localhost:4566")
                )
                .build()
            )
          )
          dynamoClient <- Resource.fromAutoCloseable(
            IO(
              DynamoDbAsyncClient
                .builder()
                .httpClient(nettyClient)
                .region(Region.US_EAST_1)
                .credentialsProvider(AwsCreds.LocalCreds)
                .endpointOverride(
                  URI.create(s"http://localhost:8000")
                )
                .build()
            )
          )
          resultsQueue <- Resource.eval(
            Queue.unbounded[IO, KinesisClientRecord]
          )
          appName = s"kinesis-mock-kcl-test-${UuidCreator.toString(UuidCreator.getTimeBased())}"
          workerId = UuidCreator.toString(UuidCreator.getTimeBased())
          retrievalSpecificConfig = new PollingConfig(
            resources.streamName.streamName,
            resources.kinesisClient
          )
          supervisor <- Supervisor[IO]
          isStarted <- Resource.eval(Deferred[IO, Unit])
          scheduler <- Resource.eval(
            IO(
              new Scheduler(
                new CheckpointConfig(),
                new CoordinatorConfig(appName)
                  .parentShardPollIntervalMillis(1000L)
                  .workerStateChangeListener(WorkerStartedListener(isStarted)),
                new LeaseManagementConfig(
                  appName,
                  dynamoClient,
                  resources.kinesisClient,
                  workerId
                ).shardSyncIntervalMillis(1000L),
                new LifecycleConfig(),
                new MetricsConfig(cloudwatchClient, appName),
                new ProcessorConfig(KCLRecordProcessorFactory(resultsQueue)),
                new RetrievalConfig(
                  resources.kinesisClient,
                  resources.streamName.streamName,
                  appName
                ).initialPositionInStreamExtended(initialPosition)
                  .retrievalSpecificConfig(retrievalSpecificConfig)
                  .retrievalFactory(retrievalSpecificConfig.retrievalFactory())
              )
            )
          )
          _ <- Resource.make(
            supervisor
              .supervise(IO(scheduler.run()))
              .flatTap(_ => isStarted.get *> IO.sleep(2.seconds))
          )(x => IO(scheduler.shutdown()) *> x.join.void)
        } yield KCLResources(resources, resultsQueue)
      }
    )

  def kclTest(resources: KCLResources): IO[Unit] = for {
    req <- IO(
      PutRecordsRequest
        .builder()
        .records(
          putRecordsRequestEntryArb.arbitrary
            .take(5)
            .toVector
            .map(x =>
              PutRecordsRequestEntry
                .builder()
                .data(SdkBytes.fromByteArray(x.data))
                .partitionKey(x.partitionKey)
                .maybeTransform(x.explicitHashKey)(_.explicitHashKey(_))
                .build()
            )
            .asJava
        )
        .streamName(resources.functionalTestResources.streamName.streamName)
        .build()
    )
    _ <- resources.functionalTestResources.kinesisClient.putRecords(req).toIO
    policy = RetryPolicies
      .limitRetries[IO](30)
      .join(RetryPolicies.constantDelay(1.second))
    gotAllRecords <- retryingOnFailures[Boolean](
      policy,
      IO.pure,
      { case (_, status) =>
        IO(
          println(
            s"Results queue is not full, retrying. Retry Status: ${status.toString}"
          )
        )
      }
    )(
      resources.resultsQueue.size.map(_ == 5)
    )
    resRecords <- for {
      rec1 <- resources.resultsQueue.take
      rec2 <- resources.resultsQueue.take
      rec3 <- resources.resultsQueue.take
      rec4 <- resources.resultsQueue.take
      rec5 <- resources.resultsQueue.take
    } yield Vector(rec1, rec2, rec3, rec4, rec5)
  } yield assert(
    gotAllRecords && resRecords
      .map(_.partitionKey())
      .diff(req.records().asScala.toVector.map(_.partitionKey()))
      .isEmpty,
    s"Got All Records: $gotAllRecords\nLength: ${resRecords.length}"
  )

  // For whatever reason, these tests seem to be flaky. Basically, a test will hit the timeout due to some
  // sort of deadlock preventing a request from being sent. This can happen in a KCL request or the PutRecords
  // call that is made. This seems to specifically happen on CI, likely due to the 1-CPU runners that are
  // employed. For now, I'm marking these as flaky, and allow for flakiness in the CI execution.
  kclFixture(
    InitialPositionInStreamExtended.newInitialPosition(
      InitialPositionInStream.TRIM_HORIZON
    )
  ).test("it should consume records".flaky)(kclTest)

  kclFixture(
    InitialPositionInStreamExtended.newInitialPositionAtTimestamp(
      Date.from(Instant.now().minusSeconds(30))
    )
  ).test("it should consume records using AT_TIMESTAMP".flaky)(kclTest)
}
