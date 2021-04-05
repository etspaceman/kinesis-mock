package kinesis.mock

import software.amazon.awssdk.services.kinesis.KinesisAsyncClient

import kinesis.mock.cache.CacheConfig
import kinesis.mock.models.StreamName

case class KinesisFunctionalTestResources(
    kinesisClient: KinesisAsyncClient,
    cacheConfig: CacheConfig,
    streamName: StreamName,
    testConfig: FunctionalTestConfig
)
