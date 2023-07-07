package kinesis.mock

import scala.concurrent.duration._
import scala.util.Try

import java.net.URI

import cats.effect.{IO, Resource, SyncIO}
import com.amazonaws.regions.Regions
import munit.{CatsEffectFunFixtures, CatsEffectSuite}
import org.scalacheck.Gen
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.awssdk.utils.AttributeMap

import kinesis.mock.cache.CacheConfig
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.AwsRegion
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

trait AwsFunctionalTests extends CatsEffectSuite with CatsEffectFunFixtures {

  protected val genStreamShardCount = 3

  // this must match env var INITIALIZE_STREAMS in docker-compose.yml
  protected val initializedStreams = Vector(
    "stream1" -> 3,
    "stream2" -> 2,
    "stream3" -> 1,
    "stream4" -> 2,
    "stream5" -> 3,
    "stream6" -> 5,
    "stream7" -> 5,
    "stream8" -> 3,
    "stream9" -> 1,
    "stream10" -> 3,
    "stream11" -> 2
  )

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

  val resource: Resource[IO, KinesisFunctionalTestResources] = for {
    testConfig <- Resource.eval(FunctionalTestConfig.read)
    protocol = if (testConfig.servicePort == 4568) "http" else "https"
    region <- Resource.eval(
      IO(
        Gen
          .oneOf(
            AwsRegion.values
              .filterNot(_ == AwsRegion.US_EAST_1)
              .filter(x => Try(Regions.fromName(x.entryName)).isSuccess)
          )
          .one
      )
    )
    kinesisClient <- Resource
      .fromAutoCloseable {
        IO(
          KinesisAsyncClient
            .builder()
            .httpClient(nettyClient)
            .region(Region.of(region.entryName))
            .credentialsProvider(AwsCreds.LocalCreds)
            .endpointOverride(
              URI.create(s"$protocol://localhost:${testConfig.servicePort}")
            )
            .build()
        )
      }
    defaultRegionKinesisClient <- Resource
      .fromAutoCloseable {
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
    cacheConfig <- Resource.eval(CacheConfig.read)
    logger <- Resource.eval(Slf4jLogger.create[IO])
    res <- Resource.make(
      IO(streamNameGen.one)
        .map(streamName =>
          KinesisFunctionalTestResources(
            kinesisClient,
            defaultRegionKinesisClient,
            cacheConfig,
            streamName,
            testConfig,
            protocol,
            logger,
            region
          )
        )
        .flatTap(setup)
    )(teardown)
  } yield res

  def setup(resources: KinesisFunctionalTestResources): IO[Unit] = for {
    _ <- resources.logger.debug(s"Creating stream ${resources.streamName}")
    _ <- resources.kinesisClient
      .createStream(
        CreateStreamRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .shardCount(genStreamShardCount)
          .build()
      )
      .toIO
    _ <- resources.logger.debug(s"Created stream ${resources.streamName}")
    _ <- IO.sleep(
      resources.cacheConfig.createStreamDuration.plus(400.millis)
    )
    _ <- resources.logger.debug(
      s"Describing stream summary for ${resources.streamName}"
    )
    streamSummary <- describeStreamSummary(resources)
    _ <- resources.logger.debug(
      s"Described stream summary for ${resources.streamName}"
    )
    res <- IO.raiseWhen(
      streamSummary
        .streamDescriptionSummary()
        .streamStatus() != StreamStatus.ACTIVE
    )(
      new RuntimeException(s"StreamStatus was not active: $streamSummary")
    )
  } yield res

  def teardown(resources: KinesisFunctionalTestResources): IO[Unit] = for {
    _ <- resources.logger.debug(s"Deleting stream ${resources.streamName}")
    _ <- resources.kinesisClient
      .deleteStream(
        DeleteStreamRequest
          .builder()
          .streamName(resources.streamName.streamName)
          .enforceConsumerDeletion(true)
          .build()
      )
      .toIO
    _ <- resources.logger.debug(s"Deleted stream ${resources.streamName}")
    _ <- IO.sleep(
      resources.cacheConfig.deleteStreamDuration.plus(400.millis)
    )
    _ <- resources.logger.debug(
      s"Describing stream summary for ${resources.streamName}"
    )
    streamSummary <- describeStreamSummary(resources).attempt
    _ <- resources.logger.debug(
      s"Described stream summary for ${resources.streamName}"
    )
    res <- IO.raiseWhen(
      streamSummary.isRight
    )(
      new RuntimeException(
        s"StreamSummary unexpectedly succeeded: $streamSummary"
      )
    )
  } yield res

  val fixture: SyncIO[FunFixture[KinesisFunctionalTestResources]] =
    ResourceFixture(resource)

  def describeStreamSummary(
      resources: KinesisFunctionalTestResources
  ): IO[DescribeStreamSummaryResponse] =
    describeStreamSummary(
      resources.kinesisClient,
      resources.streamName.streamName
    )

  def describeStreamSummary(
      kinesisClient: KinesisAsyncClient,
      streamName: String
  ): IO[DescribeStreamSummaryResponse] =
    kinesisClient
      .describeStreamSummary(
        DescribeStreamSummaryRequest
          .builder()
          .streamName(streamName)
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
