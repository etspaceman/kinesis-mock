package kinesis.mock

import java.net.URI

import cats.effect.{IO, Resource}
import munit.{CatsEffectFunFixtures, CatsEffectSuite}
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.utils.AttributeMap

trait AwsFunctionalTests extends CatsEffectFunFixtures { _: CatsEffectSuite =>
  def trustAllCertificates =
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

  val clientsFixture = ResourceFixture(
    Resource.fromAutoCloseable(
      IO(
        KinesisAsyncClient
          .builder()
          .httpClient(nettyClient)
          .region(Region.US_EAST_1)
          .credentialsProvider(AwsCreds.LocalCreds)
          .endpointOverride(URI.create(s"https://localhost:4567"))
          .build()
      )
    )
  )
}
