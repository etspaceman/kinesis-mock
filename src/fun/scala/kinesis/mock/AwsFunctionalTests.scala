package kinesis.mock

import scala.concurrent.duration._

import java.net.URI

import cats.effect.{Blocker, IO, Resource, SyncIO}
import munit.{CatsEffectFunFixtures, CatsEffectSuite}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.awssdk.utils.AttributeMap

import kinesis.mock.cache.CacheConfig
import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

trait AwsFunctionalTests extends CatsEffectFunFixtures { _: CatsEffectSuite =>
  private val trustAllCertificates =
    AttributeMap
      .builder()
      .put(
        SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
        java.lang.Boolean.TRUE
      )
      .build()

  private def nettyClient: SdkAsyncHttpClient =
    NettyNioAsyncHttpClient
      .builder()
      .buildWithDefaults(trustAllCertificates)

  val resource = for {
    blocker <- Blocker[IO]
    testConfig <- Resource.eval(FunctionalTestConfig.read(blocker))
    kinesisClient <- Resource
      .fromAutoCloseable {
        val protocol =
          if (testConfig.servicePort == 4568) "http" else "https"
        IO(
          KinesisAsyncClient
            .builder()
            .httpClient(nettyClient)
            .region(Region.US_EAST_1)
            .credentialsProvider(AwsCreds.LocalCreds)
            .endpointOverride(
              URI.create(s"$protocol://localhost:${testConfig.servicePort}")
            )
            .build()
        )
      }
    cacheConfig <- Resource.eval(CacheConfig.read(blocker))
    res <- Resource.eval(
      IO(streamNameGen.one).map(streamName =>
        KinesisFunctionalTestResources(
          kinesisClient,
          cacheConfig,
          streamName,
          testConfig
        )
      )
    )
  } yield res

  def setup(resources: KinesisFunctionalTestResources): IO[Unit] = for {
    _ <- resources.kinesisClient
      .createStream(
        CreateStreamRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .shardCount(1)
          .build()
      )
      .toIO
    _ <- IO.sleep(
      resources.cacheConfig.createStreamDuration.plus(100.millis)
    )
    streamSummary <- describeStreamSummary(resources)
    res <- IO.raiseWhen(
      streamSummary
        .streamDescriptionSummary()
        .streamStatus() != StreamStatus.ACTIVE
    )(
      new RuntimeException(s"StreamStatus was not active: $streamSummary")
    )
  } yield res

  def teardown(resources: KinesisFunctionalTestResources): IO[Unit] = for {
    _ <- resources.kinesisClient
      .deleteStream(
        DeleteStreamRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .build()
      )
      .toIO
    _ <- IO.sleep(
      resources.cacheConfig.deleteStreamDuration.plus(100.millis)
    )
    streamSummary <- describeStreamSummary(resources).attempt
    res <- IO.raiseWhen(
      streamSummary.isRight
    )(
      new RuntimeException(
        s"StreamSummary unexpectedly succeeded: $streamSummary"
      )
    )
  } yield res

  val fixture: SyncIO[FunFixture[KinesisFunctionalTestResources]] =
    ResourceFixture(
      resource,
      (_, resources) => setup(resources),
      teardown
    )

  def describeStreamSummary(
      resources: KinesisFunctionalTestResources
  ): IO[DescribeStreamSummaryResponse] =
    resources.kinesisClient
      .describeStreamSummary(
        DescribeStreamSummaryRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .build
      )
      .toIO

  def listTagsForStream(
      resources: KinesisFunctionalTestResources
  ): IO[ListTagsForStreamResponse] =
    resources.kinesisClient
      .listTagsForStream(
        ListTagsForStreamRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .build
      )
      .toIO

  def describeStreamConsumer(
      resources: KinesisFunctionalTestResources,
      consumerName: String,
      streamArn: String
  ): IO[DescribeStreamConsumerResponse] =
    resources.kinesisClient
      .describeStreamConsumer(
        DescribeStreamConsumerRequest
          .builder()
          .consumerName(consumerName)
          .streamARN(streamArn)
          .build()
      )
      .toIO

}
