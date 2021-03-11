package kinesis.mock.cache

import scala.concurrent.duration._

import ciris._

import kinesis.mock.models.AwsRegion

final case class CacheConfig(
    createStreamDuration: FiniteDuration,
    deleteStreamDuration: FiniteDuration,
    shardLimit: Int,
    awsAccountId: String,
    awsRegion: AwsRegion
)

object CacheConfig {
  def read: ConfigValue[CacheConfig] = for {
    createStreamDuration <- env("CREATE_STREAM_DURATION")
      .as[FiniteDuration]
      .default(500.millis)
    deleteStreamDuration <- env("DELETE_STREAM_DURATION")
      .as[FiniteDuration]
      .default(500.millis)
    shardLimit <- env("SHARD_LIMIT").as[Int].default(50)
    awsAccountId <- env("AWS_ACCOUNT_ID").as[String].default("000000000000")
    awsRegion <- env("AWS_REGION")
      .or(env("AWS_DEFAULT_REGION"))
      .as[AwsRegion]
      .default(AwsRegion.US_EAST_1)
  } yield CacheConfig(
    createStreamDuration,
    deleteStreamDuration,
    shardLimit,
    awsAccountId,
    awsRegion
  )
}
