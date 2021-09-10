package kinesis.mock

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient

import kinesis.mock.cache.CacheConfig
import kinesis.mock.models.StreamName

case class KinesisFunctionalTestResources(
    kinesisClient: KinesisAsyncClient,
    defaultRegionKinesisClient: KinesisAsyncClient,
    cacheConfig: CacheConfig,
    streamName: StreamName,
    testConfig: FunctionalTestConfig,
    httpProtocol: String,
    logger: SelfAwareStructuredLogger[IO],
    awsRegion: Region
)
