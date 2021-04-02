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
import kinesis.mock.models.StreamName
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

trait AwsFunctionalTests extends CatsEffectFunFixtures { _: CatsEffectSuite =>
  private def trustAllCertificates =
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

  case class KinesisFunctionalTestResources(
      kinesisClient: KinesisAsyncClient,
      cacheConfig: CacheConfig,
      streamName: StreamName
  )

  val fixture: SyncIO[FunFixture[KinesisFunctionalTestResources]] =
    ResourceFixture(
      Resource
        .fromAutoCloseable(
          IO(
            KinesisAsyncClient
              .builder()
              .httpClient(nettyClient)
              .region(Region.US_EAST_1)
              .credentialsProvider(AwsCreds.LocalCreds)
              .endpointOverride(URI.create(s"https://localhost:4568"))
              .build()
          )
        )
        .parZip(Blocker[IO].evalMap(CacheConfig.read))
        .evalMap { case (client, config) =>
          IO(streamNameGen.one).map(streamName =>
            KinesisFunctionalTestResources(client, config, streamName)
          )
        },
      (_, resources) =>
        for {
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
            resources.cacheConfig.createStreamDuration.plus(20.millis)
          )
          streamSummary <- resources.kinesisClient
            .describeStreamSummary(
              DescribeStreamSummaryRequest
                .builder()
                .streamName(resources.streamName.streamName)
                .build()
            )
            .toIO
          res <- IO.raiseWhen(
            streamSummary
              .streamDescriptionSummary()
              .streamStatus() != StreamStatus.ACTIVE
          )(
            new RuntimeException(s"StreamStatus was not active: $streamSummary")
          )
        } yield res,
      resources =>
        for {
          _ <- resources.kinesisClient
            .deleteStream(
              DeleteStreamRequest
                .builder()
                .streamName(resources.streamName.streamName)
                .build()
            )
            .toIO
          _ <- IO.sleep(
            resources.cacheConfig.deleteStreamDuration.plus(20.millis)
          )
          streamSummary <- resources.kinesisClient
            .describeStreamSummary(
              DescribeStreamSummaryRequest
                .builder()
                .streamName(resources.streamName.streamName)
                .build()
            )
            .toIO
            .attempt
          res <- IO.raiseWhen(
            streamSummary.isRight
          )(
            new RuntimeException(
              s"StreamSummary unexpectedly succeeded: $streamSummary"
            )
          )
        } yield res
    )
}
