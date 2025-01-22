package kinesis.mock

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import java.net.URI
import java.time.Instant
import java.util.Date

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Resource}
import retry._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.kinesis.checkpoint.CheckpointConfig
import software.amazon.kinesis.common._
import software.amazon.kinesis.coordinator.{CoordinatorConfig, Scheduler}
import software.amazon.kinesis.leases.LeaseManagementConfig
import software.amazon.kinesis.leases.dynamodb.{
  DynamoDBLeaseManagementFactory,
  DynamoDBLeaseSerializer
}
import software.amazon.kinesis.lifecycle.LifecycleConfig
import software.amazon.kinesis.metrics.MetricsConfig
import software.amazon.kinesis.processor.ProcessorConfig
import software.amazon.kinesis.processor.SingleStreamTracker
import software.amazon.kinesis.retrieval.polling.PollingConfig
import software.amazon.kinesis.retrieval.{KinesisClientRecord, RetrievalConfig}

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.id._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class KCLTests extends AwsFunctionalTests {
  override val munitIOTimeout = 4.minutes

  def kclFixture(initialPosition: InitialPositionInStreamExtended) =
    ResourceFunFixture(
      resource.flatMap { resources =>
        for {
          cloudwatchClient <- Resource.fromAutoCloseable(
            IO(
              CloudWatchAsyncClient
                .builder()
                .httpClient(nettyClient)
                .region(resources.sdkRegion)
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
                .region(resources.sdkRegion)
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
          appName = s"kinesis-mock-kcl-test-${Utils.randomUUIDString}"
          workerId = Utils.randomUUIDString
          retrievalSpecificConfig = new PollingConfig(
            resources.streamName.streamName,
            resources.kinesisClient
          )
          isStarted <- Resource.eval(Deferred[IO, Unit])
          defaultLeaseManagement = new LeaseManagementConfig(
            appName,
            appName,
            dynamoClient,
            resources.kinesisClient,
            workerId
          ).shardSyncIntervalMillis(1000L)
          leaseManagementConfig = defaultLeaseManagement.leaseManagementFactory(
            new DynamoDBLeaseManagementFactory(
              defaultLeaseManagement.kinesisClient(),
              defaultLeaseManagement.dynamoDBClient(),
              defaultLeaseManagement.tableName(),
              defaultLeaseManagement.workerIdentifier(),
              defaultLeaseManagement.executorService(),
              defaultLeaseManagement.failoverTimeMillis(),
              defaultLeaseManagement.enablePriorityLeaseAssignment(),
              defaultLeaseManagement.epsilonMillis(),
              defaultLeaseManagement.maxLeasesForWorker(),
              defaultLeaseManagement.maxLeasesToStealAtOneTime(),
              defaultLeaseManagement.maxLeaseRenewalThreads(),
              defaultLeaseManagement.cleanupLeasesUponShardCompletion(),
              defaultLeaseManagement.ignoreUnexpectedChildShards(),
              defaultLeaseManagement.shardSyncIntervalMillis(),
              defaultLeaseManagement.consistentReads(),
              defaultLeaseManagement.listShardsBackoffTimeInMillis(),
              defaultLeaseManagement.maxListShardsRetryAttempts(),
              defaultLeaseManagement.maxCacheMissesBeforeReload(),
              defaultLeaseManagement.listShardsCacheAllowedAgeInSeconds(),
              defaultLeaseManagement.cacheMissWarningModulus(),
              defaultLeaseManagement.initialLeaseTableReadCapacity().toLong,
              defaultLeaseManagement.initialLeaseTableWriteCapacity().toLong,
              defaultLeaseManagement.tableCreatorCallback(),
              defaultLeaseManagement.dynamoDbRequestTimeout(),
              defaultLeaseManagement.billingMode(),
              defaultLeaseManagement.leaseTableDeletionProtectionEnabled(),
              defaultLeaseManagement.leaseTablePitrEnabled(),
              defaultLeaseManagement.tags(),
              new DynamoDBLeaseSerializer(),
              defaultLeaseManagement.customShardDetectorProvider(),
              false,
              LeaseCleanupConfig
                .builder()
                .completedLeaseCleanupIntervalMillis(500L)
                .garbageLeaseCleanupIntervalMillis(500L)
                .leaseCleanupIntervalMillis(10.seconds.toMillis)
                .build(),
              defaultLeaseManagement
                .workerUtilizationAwareAssignmentConfig()
                .disableWorkerMetrics(true),
              defaultLeaseManagement.gracefulLeaseHandoffConfig()
            )
          )
          scheduler <- Resource.eval(
            IO(
              new Scheduler(
                new CheckpointConfig(),
                new CoordinatorConfig(appName)
                  .parentShardPollIntervalMillis(1000L)
                  .workerStateChangeListener(WorkerStartedListener(isStarted)),
                leaseManagementConfig,
                new LifecycleConfig(),
                new MetricsConfig(cloudwatchClient, appName),
                new ProcessorConfig(KCLRecordProcessorFactory(resultsQueue)),
                new RetrievalConfig(
                  resources.kinesisClient,
                  new SingleStreamTracker(
                    StreamIdentifier.singleStreamInstance(
                      resources.streamName.streamName
                    ),
                    initialPosition
                  ),
                  appName
                )
                  .retrievalSpecificConfig(retrievalSpecificConfig)
                  .retrievalFactory(retrievalSpecificConfig.retrievalFactory())
              )
            )
          )
          _ <- for {
            _ <- Resource.eval(resources.logger.info("Starting KCL Scheduler"))
            _ <- IO.blocking(scheduler.run()).background
            _ <- Resource.onFinalize(
              for {
                _ <- resources.logger.info("Shutting down KCL Scheduler")
                _ <- scheduler.startGracefulShutdown().toIO
                _ <- resources.logger.info("KCL Scheduler has been shut down")
                _ <- IO.blocking(scheduler.shutdown())
              } yield ()
            )
            _ <- Resource.eval(
              for {
                _ <- resources.logger.info("Checking if KCL is started")
                _ <- isStarted.get
                _ <- resources.logger.info("KCL is started")
              } yield ()
            )
          } yield ()
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
    _ <- resources.functionalTestResources.logger.debug(
      s"Scaling stream ${resources.functionalTestResources.streamName}"
    )
    _ <- resources.functionalTestResources.kinesisClient
      .updateShardCount(
        UpdateShardCountRequest
          .builder()
          .streamName(resources.functionalTestResources.streamName.streamName)
          .targetShardCount(2)
          .scalingType(ScalingType.UNIFORM_SCALING)
          .build()
      )
      .toIO
    _ <- IO.sleep(2.seconds)
    _ <- resources.functionalTestResources.logger.debug(
      s"Putting records to ${resources.functionalTestResources.streamName}"
    )
    _ <- resources.functionalTestResources.kinesisClient.putRecords(req).toIO
    _ <- resources.functionalTestResources.logger.debug(
      s"Put records to ${resources.functionalTestResources.streamName}"
    )
    policy = RetryPolicies
      .limitRetries[IO](30)
      .join(RetryPolicies.constantDelay(1.second))
    gotAllRecords <- retryingOnFailures[Boolean](
      policy,
      IO.pure,
      { case (_, status) =>
        resources.resultsQueue.size.flatMap(queueSize =>
          resources.functionalTestResources.logger.debug(
            s"Results queue is not full, retrying. Retry Status: ${status.toString}. Result Queue Size: $queueSize"
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
    _ <- resources.functionalTestResources.logger.debug(
      s"Result:\n${resRecords.mkString("\n")}"
    )
  } yield assert(
    gotAllRecords && resRecords
      .map(_.partitionKey())
      .diff(req.records().asScala.toVector.map(_.partitionKey()))
      .isEmpty,
    s"Got All Records: $gotAllRecords\nLength: ${resRecords.length}"
  )

  kclFixture(
    InitialPositionInStreamExtended.newInitialPosition(
      InitialPositionInStream.TRIM_HORIZON
    )
  ).test("it should consume records")(kclTest)

  kclFixture(
    InitialPositionInStreamExtended.newInitialPositionAtTimestamp(
      Date.from(Instant.now().minusSeconds(30))
    )
  ).test("it should consume records using AT_TIMESTAMP")(kclTest)
}
