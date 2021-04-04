package kinesis.mock

import scala.concurrent.duration._

import java.net.URI

import cats.effect.{Blocker, IO, Resource, SyncIO}
import munit.{CatsEffectFunFixtures, CatsEffectSuite}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.http.{Protocol, SdkHttpConfigurationOption}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model._
import software.amazon.awssdk.utils.AttributeMap

import kinesis.mock.cache.CacheConfig
import kinesis.mock.instances.arbitrary._
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

  private def nettyClient(port: Int): SdkAsyncHttpClient = {
    val protocol = if (port == 4567) Protocol.HTTP2 else Protocol.HTTP1_1
    NettyNioAsyncHttpClient
      .builder()
      .protocol(protocol)
      .buildWithDefaults(trustAllCertificates)
  }

  val fixture: SyncIO[FunFixture[KinesisFunctionalTestResources]] =
    ResourceFixture(
      Blocker[IO].flatMap(blocker =>
        Resource
          .fromAutoCloseable(
            FunctionalTestConfig
              .read(blocker)
              .map { config =>
                val protocol =
                  if (config.servicePort == 4568) "http" else "https"
                KinesisAsyncClient
                  .builder()
                  .httpClient(nettyClient(config.servicePort))
                  .region(Region.US_EAST_1)
                  .credentialsProvider(AwsCreds.LocalCreds)
                  .endpointOverride(
                    URI.create(s"$protocol://localhost:${config.servicePort}")
                  )
                  .build()
              }
          )
          .parZip(Blocker[IO].evalMap(CacheConfig.read))
          .evalMap { case (client, config) =>
            IO(streamNameGen.one).map(streamName =>
              KinesisFunctionalTestResources(client, config, streamName)
            )
          }
      ),
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
            resources.cacheConfig.createStreamDuration.plus(50.millis)
          )
          streamSummary <- describeStreamSummary(resources)
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
            resources.cacheConfig.deleteStreamDuration.plus(50.millis)
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
