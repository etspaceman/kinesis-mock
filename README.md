# Kinesis Mock

- [Overview](#overview)
- [Starting the service](#starting-the-service)
- [Usage](#usage)
  * [Configuring AWS SDK Kinesis Client](#configuring-aws-sdk-kinesis-client)
  * [Configuring the KPL](#configuring-the-kpl)
  * [Configuring the KCL](#configuring-the-kcl)

# Overview

A mock for the [Kinesis](https://docs.aws.amazon.com/kinesis/latest/APIReference/Welcome.html) API, intended for local testing.

# Starting the service

It is available as a docker image in the GitHub Container Registry:

```shell
docker pull ghcr.io/etspaceman/kinesis-mock:0.0.1
docker run -p 4567:4567 -p 4568:4568 ghcr.io/etspaceman/kinesis-mock:0.0.1
```

You can also leverage the `kinesis-mock.jar` executable in the release assets:

```shell
java -jar ./kinesis-mock.jar
```

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
      .endpointOverride(URI.create(s"https://localhost:8000")) // dynamodb-local port
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



