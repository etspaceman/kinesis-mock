/*
 * Copyright 2021-2026 io.github.etspaceman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import java.net.URI

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Resource, SyncIO}
import cats.syntax.all.*
import org.scalacheck.Arbitrary
import retry.*
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.model.*
import software.amazon.kinesis.checkpoint.CheckpointConfig
import software.amazon.kinesis.common.*
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
import software.amazon.kinesis.retrieval.KinesisClientRecord
import software.amazon.kinesis.retrieval.RetrievalConfig
import software.amazon.kinesis.retrieval.fanout.FanOutConfig

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.syntax.id.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class KCLFanOutTests extends AwsFunctionalTests:
  override val munitIOTimeout: FiniteDuration = 4.minutes

  def kclFanOutFixture: SyncIO[FunFixture[KCLResources]] =
    ResourceFunFixture(
      resource().flatMap { resources =>
        val initialPosition = InitialPositionInStreamExtended
          .newInitialPosition(InitialPositionInStream.TRIM_HORIZON)
        for
          cloudwatchClient <- Resource.fromAutoCloseable(
            IO(
              CloudWatchAsyncClient
                .builder()
                .httpClient(nettyClient)
                .region(resources.sdkRegion)
                .credentialsProvider(AwsCreds.LocalCreds)
                .endpointOverride(URI.create(s"https://localhost:4566"))
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
                .endpointOverride(URI.create(s"http://localhost:8000"))
                .build()
            )
          )
          resultsQueue <- Resource.eval(
            Queue.unbounded[IO, KinesisClientRecord]
          )
          appNameId <- Utils.randomUUIDString.toResource
          appName = s"kinesis-mock-kcl-fanout-test-$appNameId"
          workerId <- Utils.randomUUIDString.toResource
          retrievalSpecificConfig =
            new FanOutConfig(resources.kinesisClient)
              .streamName(resources.streamName.streamName)
              .applicationName(appName)
          isStarted <- Resource.eval(Deferred[IO, Unit])
          defaultLeaseManagement = new LeaseManagementConfig(
            appName,
            appName,
            dynamoClient,
            resources.kinesisClient,
            workerId
          ).shardSyncIntervalMillis(1000L)
            .failoverTimeMillis(1000L)
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
              defaultLeaseManagement.gracefulLeaseHandoffConfig(),
              defaultLeaseManagement.leaseAssignmentIntervalMillis()
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
          _ <-
            for
              _ <- Resource.eval(
                resources.logger.info("Starting KCL FanOut Scheduler")
              )
              _ <- IO.blocking(scheduler.run()).background
              _ <- Resource.onFinalize(
                for
                  _ <- resources.logger.info(
                    "Shutting down KCL FanOut Scheduler"
                  )
                  _ <- scheduler.startGracefulShutdown().toIO
                  _ <- resources.logger.info(
                    "KCL FanOut Scheduler has been shut down"
                  )
                  _ <- IO.blocking(scheduler.shutdown())
                yield ()
              )
              _ <- Resource.eval(
                for
                  _ <- resources.logger.info(
                    "Checking if KCL FanOut is started"
                  )
                  _ <- isStarted.get
                  _ <- resources.logger.info("KCL FanOut is started")
                yield ()
              )
            yield ()
        yield KCLResources(resources, resultsQueue)
      }
    )

  kclFanOutFixture.test(
    "it should consume records via enhanced fan-out (SubscribeToShard)"
  ) { resources =>
    for
      putRequests <- IO(
        Arbitrary
          .arbitrary[kinesis.mock.api.PutRecordsRequestEntry]
          .take(5)
          .toVector
      )
      req = PutRecordsRequest
        .builder()
        .records(
          putRequests
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
      _ <- resources.functionalTestResources.kinesisClient.putRecords(req).toIO
      policy = RetryPolicies
        .limitRetries[IO](90)
        .join(RetryPolicies.constantDelay(1.second))
      gotAllRecords <- retryingOnFailures[Boolean](
        policy,
        IO.pure,
        { case (_, status) =>
          resources.resultsQueue.size.flatMap(queueSize =>
            resources.functionalTestResources.logger.debug(
              s"FanOut queue size $queueSize, retry status: $status"
            )
          )
        }
      )(resources.resultsQueue.size.map(_ >= 5))
      received <- (1 to 5).toVector.traverse(_ => resources.resultsQueue.take)
    yield
      assert(gotAllRecords, s"did not receive 5 records via fan-out")
      val receivedKeys = received.map(_.partitionKey()).toSet
      val expectedKeys = putRequests.map(_.partitionKey).toSet
      assert(
        expectedKeys.subsetOf(receivedKeys),
        s"missing partition keys. expected ${expectedKeys}, got ${receivedKeys}"
      )
  }
