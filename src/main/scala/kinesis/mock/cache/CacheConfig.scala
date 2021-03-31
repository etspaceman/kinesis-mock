package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, ContextShift, IO}
import pureconfig._
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.module.enumeratum._

import kinesis.mock.models._

final case class CacheConfig(
    createStreamDuration: FiniteDuration,
    deleteStreamDuration: FiniteDuration,
    registerStreamConsumerDuration: FiniteDuration,
    deregisterStreamConsumerDuration: FiniteDuration,
    startStreamEncryptionDuration: FiniteDuration,
    stopStreamEncryptionDuration: FiniteDuration,
    mergeShardsDuration: FiniteDuration,
    splitShardDuration: FiniteDuration,
    updateShardCountDuration: FiniteDuration,
    shardLimit: Int,
    awsAccountId: AwsAccountId,
    awsRegion: AwsRegion
)

object CacheConfig {
  implicit val cacheConfigReader: ConfigReader[CacheConfig] = deriveReader
  def read(blocker: Blocker)(implicit CS: ContextShift[IO]): IO[CacheConfig] =
    ConfigSource.resources("cache.conf").loadF[IO, CacheConfig](blocker)
}
