# Kinesis Mock

![](https://github.com/etspaceman/kinesis-mock/workflows/Scala%20CI/badge.svg)
[![codecov](https://codecov.io/gh/etspaceman/kinesis-mock/branch/main/graph/badge.svg?token=XH58VN2O49)](https://codecov.io/gh/etspaceman/kinesis-mock)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Join the chat at https://gitter.im/etspaceman/kinesis-mock](https://badges.gitter.im/etspaceman/kinesis-mock.svg)](https://gitter.im/etspaceman/kinesis-mock?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

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

It is available as a docker image in the GitHub Container Registry:

```shell
docker pull ghcr.io/etspaceman/kinesis-mock:0.0.2
docker run -p 4567:4567 -p 4568:4568 ghcr.io/etspaceman/kinesis-mock:0.0.2
```

You can also leverage the `kinesis-mock.jar` executable in the release assets:

```shell
java -jar ./kinesis-mock.jar
```

# Service Configuration

Below is the available configuration for the service. Note that it is not recommended to edit the ports in the docker environment (rather you can map
these ports to a local one).

| Variable | Data Type | Default Value | Notes |
| -------- | --------- | ------------- | ----- |
| KINESIS_MOCK_HTTP2_PORT | Int | 4567 | Https Only |
| KINESIS_MOCK_HTTP1_PLAIN_PORT | Int | 4568 | Http Only |
| CREATE_STREAM_DURATION | Duration | 500ms | |
| DELETE_STREAM_DURATION | Duration | 500ms | |
| REGISTER_STREAM_CONSUMER_DURATION | Duration | 500ms | |
| START_STREAM_ENCRYPTION_DURATION | Duration | 500ms | |
| STOP_STREAM_ENCRYPTION_DURATION | Duration | 500ms | |
| DEREGISTER_STREAM_CONSUMER_DURATION | Duration | 500ms | |
| MERGE_SHARDS_DURATION | Duration | 500ms | |
| SPLIT_SHARD_DURATION | Duration | 500ms | |
| UPDATE_SHARD_COUNT_DURATION | Duration | 500ms | |
| SHARD_LIMIT | Int | 50 | |
| AWS_ACCOUNT_ID | String | "000000000000" | |
| AWS_REGION | String | "us-east-1" | |

# Usage

The image exposes 2 ports for interactions:
- 4567 (https)
- 4568 (http)

For an example docker-compose setup which uses this image, check out the [docker-compose.yml](docker/docker-compose.yml) file.

There are examples configuring the KPL, KCL and AWS SDK to use this mock in the [functional tests](src/fun/scala/kinesis/mock).

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
      AwsCreds("mock-kinesis-access-key", "mock-kinesis-secret-key")
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
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}

object MyApp {
  // A mock credentials provider
  final case class AwsCreds(accessKey: String, secretKey: String)
    extends AWSCredentials
    with AWSCredentialsProvider {
    override def getAWSAccessKeyId: String = accessKey
    override def getAWSSecretKey: String = secretKey
    override def getCredentials: AWSCredentials = this
    override def refresh(): Unit = ()
  }

  object AwsCreds {
    val LocalCreds: AwsCreds =
      AwsCreds("mock-kinesis-access-key", "mock-kinesis-secret-key")
  }
  
  val kplProducer = new KinesisProducer(
    new KinesisProducerConfiguration()
      .setCredentialsProvider(AwsCreds.LocalCreds)
      .setRegion(Regions.US_EAST_1.getName)
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
      AwsCreds("mock-kinesis-access-key", "mock-kinesis-secret-key")
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
  
  // Consumer can be executed from this by running scheduler.run()
  val scheduler = new Scheduler(
    new CheckpointConfig(),
    new CoordinatorConfig(appName)
    .parentShardPollIntervalMillis(1000L),
    new LeaseManagementConfig(
      appName,
      dynamoClient,
      kinesisClient,
      workerId
    ).shardSyncIntervalMillis(1000L),
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
- Does not currently support Http2 requests (https://github.com/http4s/http4s/issues/4707)
- Does not currently support SubscribeToShard due to lack of push-promise support (https://github.com/http4s/http4s/issues/4624)
