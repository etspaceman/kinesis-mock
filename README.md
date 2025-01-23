# Kinesis Mock

![](https://github.com/etspaceman/kinesis-mock/workflows/Scala%20CI/badge.svg)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Join the chat at https://gitter.im/etspaceman/kinesis-mock](https://badges.gitter.im/etspaceman/kinesis-mock.svg)](https://gitter.im/etspaceman/kinesis-mock?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
![](https://img.shields.io/github/downloads/etspaceman/kinesis-mock/total)
[![npm version](https://badge.fury.io/js/kinesis-local.svg)](https://badge.fury.io/js/kinesis-local)

- [Kinesis Mock](#kinesis-mock)
- [Overview](#overview)
- [Starting the service](#starting-the-service)
- [Service Configuration](#service-configuration)
- [Usage](#usage)
  * [Configuring AWS SDK Kinesis Client](#configuring-aws-sdk-kinesis-client)
  * [Configuring the KPL](#configuring-the-kpl)
  * [Configuring the KCL](#configuring-the-kcl)
- [Known issues](#known-issues)

# Overview

A mock for the [Kinesis](https://docs.aws.amazon.com/kinesis/latest/APIReference/Welcome.html) API, intended for local testing.

# Starting the service

There are a few ways to start kinesis-mock.

## Docker

It is available as a docker image in the GitHub Container Registry:

```shell
docker pull ghcr.io/etspaceman/kinesis-mock:0.4.1
docker run -p 4567:4567 -p 4568:4568 ghcr.io/etspaceman/kinesis-mock:0.4.1
```

## NPM

It is available on [NPM](https://www.npmjs.com/package/kinesis-local) as an executable service.

```shell
npm i kinesis-local
npx kinesis-local
```

## Manual

You can also leverage the following executable options in the release assets:

| File | Description | Launching |
| ---- | ----------- | --------- |
| `main.js` | Executable NodeJS file that can be run in any NodeJS enabled environment | `node ./main.js` |
| `main.js.map` | Source mappings for main.js | |
| `server.json` | self-signed certificate for TLS. Should be included in the same area as `main.js` | |

# Service Configuration

Below is the available configuration for the service. Note that it is not recommended to edit the ports in the docker environment (rather you can map
these ports to a local one).

| Variable | Data Type | Default Value | Notes                                                                                                                                                                                                                                                                                                                                                   |
| -------- | --------- | ------------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| INITIALIZE_STREAMS | String | | A comma-delimited string of stream names, its optional corresponding shard count and an optional region to initialize during startup. If the shard count is not provided, the default shard count of 4 is used. If the region is not provided, the default region is used. For example: "my-first-stream:1,my-other-stream::us-west-2,my-last-stream:1" |
| KINESIS_MOCK_TLS_PORT | Int | 4567 | Https Only                                                                                                                                                                                                                                                                                                                                              |
| KINESIS_MOCK_PLAIN_PORT | Int | 4568 | Http Only                                                                                                                                                                                                                                                                                                                                               |
| KINESIS_MOCK_KEYSTORE_PASSWORD | Int | <redacted> | Password for the JKS KeyStore (only for JVM, not JS)
| KINESIS_MOCK_KEYMANAGER_PASSWORD | Int | <redacted> | Password for the JKS KeyManager (only for JVM, not JS)
| KINESIS_MOCK_CERT_PASSWORD | Int | <redacted> | Password used for self-signed certificate (only for JS, not JVM)
| KINESIS_MOCK_CERT_PATH | Int | server.json | Path to certificate file (only for JS, not JVM)
| CREATE_STREAM_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| DELETE_STREAM_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| REGISTER_STREAM_CONSUMER_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| START_STREAM_ENCRYPTION_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| STOP_STREAM_ENCRYPTION_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| DEREGISTER_STREAM_CONSUMER_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| MERGE_SHARDS_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| SPLIT_SHARD_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| UPDATE_SHARD_COUNT_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| UPDATE_STREAM_MODE_DURATION | Duration | 500ms |                                                                                                                                                                                                                                                                                                                                                         |
| SHARD_LIMIT | Int | 50 |                                                                                                                                                                                                                                                                                                                                                         |
| ON_DEMAND_STREAM_COUNT_LIMIT | Int | 10 |                                                                                                                                                                                                                                                                                                                                                         |
| AWS_ACCOUNT_ID | String | "000000000000" |                                                                                                                                                                                                                                                                                                                                                         |
| AWS_REGION | String | "us-east-1" | Default region in use for operations. E.g. if a region is not provided by the INITIALIZE_STREAMS values.                                                                                                                                                                                                                                                |
| LOG_LEVEL| String | "INFO" | Sets the log-level for kinesis-mock specific logs                                                                                                                                                                                                                                                                                                       |
| ROOT_LOG_LEVEL | String | "ERROR" | Sets the log-level for all dependencies                                                                                                                                                                                                                                                                                                                 |
| LOAD_DATA_IF_EXISTS | Boolean | true | Loads data from the configured persisted data file if it exists                                                                                                                                                                                                                                                                                         |
| SHOULD_PERSIST_DATA | Boolean | false | Persists data to disk. Used to keep data during restarts of the service                                                                                                                                                                                                                                                                                 |
| PERSIST_PATH | String | "data" | Path to persist data to. If it doesn't start with "/", the path is considered relative to the present working directory.                                                                                                                                                                                                                                |
| PERSIST_FILE_NAME | String | "kinesis-data.json" | File name for persisted data                                                                                                                                                                                                                                                                                                                            |
| PERSIST_INTERVAL | Duration | 5s | Delay between data persistence                                                                                                                                                                                                                                                                                                                          |


## Log Levels

You can configure the `LOG_LEVEL` of the mock with the following levels in mind:

* `ERROR`- Unhandled errors in the service
* `WARN` - Handled errors in the service (e.g. bad requests)
* `INFO` - High-level, low-noise informational messages (default)
* `DEBUG` - Low-level, high-noise informational messages
* `TRACE` - Log data bodies going in / out of the service

# Usage

The image exposes 2 ports for interactions:
- 4567 (https)
- 4568 (http)

For an example docker-compose setup which uses this image, check out the [docker-compose.yml](docker/docker-compose.yml) file.

There are examples configuring the KPL, KCL and AWS SDK to use this mock in the [integration tests](integration-tests/src/test/scala/kinesis/mock).

## Configuring AWS SDK Kinesis Client

```scala
import software.amazon.awssdk.auth.credentials.{AwsCredentials,AwsCredentialsProvider}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.awssdk.utils.AttributeMap

object MyApp {
  // A mock credentials provider
  final case class AwsCreds(accessKey: String, secretKey: String)
    extends AwsCredentials
    with AwsCredentialsProvider {
    override def accessKeyId(): String = accessKey
    override def secretAccessKey(): String = secretKey
    override def resolveCredentials(): AwsCredentials = this
  }

  object AwsCreds {
    val LocalCreds: AwsCreds =
      AwsCreds("mockKinesisAccessKey", "mockKinesisSecretKey")
  }
  
  // The kinesis-mock uses a self-signed certificate
  private val trustAllCertificates =
    AttributeMap
      .builder()
      .put(
        SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
        java.lang.Boolean.TRUE
      )
      .build()
  
  def nettyClient: SdkAsyncHttpClient =
    NettyNioAsyncHttpClient
      .builder()
      .buildWithDefaults(trustAllCertificates)

  val kinesisClient: KinesisAsyncClient = 
    KinesisAsyncClient
        .builder()
        .httpClient(nettyClient)
        .region(Region.US_EAST_1)
        .credentialsProvider(AwsCreds.LocalCreds)
        .endpointOverride(URI.create(s"https://localhost:4567"))
        .build()
}
```

## Configuring the KPL
```scala
import software.amazon.awssdk.auth.credentials.{AwsCredentials,AwsCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.kinesis.producer._

object MyApp {
  // A mock credentials provider
  final case class AwsCreds(accessKey: String, secretKey: String)
    extends AwsCredentials
    with AwsCredentialsProvider {
    override def accessKeyId(): String = accessKey
    override def secretAccessKey(): String = secretKey
    override def resolveCredentials(): AwsCredentials = this
  }

  object AwsCreds {
    val LocalCreds: AwsCreds =
      AwsCreds("mockKinesisAccessKey", "mockKinesisSecretKey")
  }
  
  val kplProducer = new KinesisProducer(
    new KinesisProducerConfiguration()
      .setCredentialsProvider(AwsCreds.LocalCreds)
      .setRegion(Region.US_EAST_1.id())
      .setKinesisEndpoint("localhost")
      .setKinesisPort(4567L)
      .setCloudwatchEndpoint("localhost")
      .setCloudwatchPort(4566L) // Using localstack's Cloudwatch port
      .setVerifyCertificate(false)
    )
}
```

## Configuring the KCL

```scala
import software.amazon.awssdk.auth.credentials.{AwsCredentials,AwsCredentialsProvider}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.awssdk.utils.AttributeMap
import software.amazon.kinesis.checkpoint.CheckpointConfig
import software.amazon.kinesis.common._
import software.amazon.kinesis.coordinator.{CoordinatorConfig, Scheduler}
import software.amazon.kinesis.leases.LeaseManagementConfig
import software.amazon.kinesis.leases.dynamodb.{
  DynamoDBLeaseManagementFactory,
  DynamoDBLeaseSerializer
}
import software.amazon.kinesis.lifecycle.LifecycleConfig
import software.amazon.kinesis.lifecycle.events._
import software.amazon.kinesis.metrics.MetricsConfig
import software.amazon.kinesis.processor._
import software.amazon.kinesis.retrieval.polling.PollingConfig
import software.amazon.kinesis.retrieval.RetrievalConfig

object MyApp {
  // A mock credentials provider
  final case class AwsCreds(accessKey: String, secretKey: String)
    extends AwsCredentials
    with AwsCredentialsProvider {
    override def accessKeyId(): String = accessKey
    override def secretAccessKey(): String = secretKey
    override def resolveCredentials(): AwsCredentials = this
  }

  object AwsCreds {
    val LocalCreds: AwsCreds =
      AwsCreds("mockKinesisAccessKey", "mockKinesisSecretKey")
  }
  
  // The kinesis-mock uses a self-signed certificate
  private val trustAllCertificates =
    AttributeMap
      .builder()
      .put(
        SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
        java.lang.Boolean.TRUE
      )
      .build()
  
  def nettyClient: SdkAsyncHttpClient =
    NettyNioAsyncHttpClient
      .builder()
      .buildWithDefaults(trustAllCertificates)

  val kinesisClient: KinesisAsyncClient = 
    KinesisAsyncClient
      .builder()
      .httpClient(nettyClient)
      .region(Region.US_EAST_1)
      .credentialsProvider(AwsCreds.LocalCreds)
      .endpointOverride(URI.create(s"https://localhost:4567"))
      .build()

  val cloudwatchClient: CloudWatchAsyncClient - 
    CloudWatchAsyncClient
      .builder()
      .httpClient(nettyClient)
      .region(Region.US_EAST_1)
      .credentialsProvider(AwsCreds.LocalCreds)
      .endpointOverride(URI.create(s"https://localhost:4566")) // localstack port
      .build()

  val dynamoClient: DynamoDbAsyncClient - 
    DynamoDbAsyncClient
      .builder()
      .httpClient(nettyClient)
      .region(Region.US_EAST_1)
      .credentialsProvider(AwsCreds.LocalCreds)
      .endpointOverride(URI.create(s"http://localhost:8000")) // dynamodb-local port
      .build()
    
  object KCLRecordProcessor extends ShardRecordProcessor {
    override def initialize(x: InitializationInput): Unit = ()
    override def processRecords(x: ProcessRecordsInput): Unit = println(s"GOT RECORDS: $x")
    override def leaseLost(x: LeaseLostInput): Unit = ()
    override def shardEnded(x: ShardEndedInput): Unit = ()
    override def shutdownRequested(x: ShutdownRequestedInput): Unit = ()
  }

  object KCLRecordProcessorFactory extends ShardRecordProcessorFactory {
    override def shardRecordProcessor(): ShardRecordProcessor =
      KCLRecordProcessor
    override def shardRecordProcessor(
      streamIdentifier: StreamIdentifier
    ): ShardRecordProcessor = KCLRecordProcessor
  }

  val appName = "some-app-name"
  val workerId = "some-worker-id"
  val streamName = "some-stream-name"
  // kinesis-mock only supports polling consumers today
  val retrievalSpecificConfig = new PollingConfig(streamName, kinesisClient)
  // Local setups require us to tweak the lease management configuration
  val defaultLeaseManagement = new LeaseManagementConfig(
    appName,
    appName,
    dynamoClient,
    kinesisClient,
    workerId
  ).shardSyncIntervalMillis(1000L)
    .failoverTimeMillis(1000L)
  val leaseManagementConfig = defaultLeaseManagement.leaseManagementFactory(
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
  
  // Consumer can be executed from this by running scheduler.run()
  val scheduler = new Scheduler(
    new CheckpointConfig(),
    new CoordinatorConfig(appName)
    .parentShardPollIntervalMillis(1000L),
    leaseManagementConfig,
    new LifecycleConfig(),
    new MetricsConfig(cloudwatchClient, appName),
    new ProcessorConfig(KCLRecordProcessorFactory),
    new RetrievalConfig(
      kinesisClient,
      streamName,
      appName
    ).initialPositionInStreamExtended(
    InitialPositionInStreamExtended.newInitialPosition(
        InitialPositionInStream.TRIM_HORIZON
    )
    ).retrievalSpecificConfig(retrievalSpecificConfig)
    .retrievalFactory(retrievalSpecificConfig.retrievalFactory())
  )
}
```

# Known issues
- Does not currently support SubscribeToShard due to lack of push-promise support (https://github.com/http4s/http4s/issues/4624)
