package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import io.circe.Encoder
import io.circe.derivation._
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.module.enumeratum._

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.instances.circe._
import kinesis.mock.models._

final case class CacheConfig(
    initializeStreams: Option[List[CreateStreamRequest]],
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
    awsRegion: AwsRegion,
    persistConfig: PersistConfig
)

object CacheConfig {
  implicit val cacheConfigReader: ConfigReader[CacheConfig] = deriveReader
  implicit val cacheConfigCirceEncoder: Encoder[CacheConfig] = deriveEncoder

  implicit val initializeStreamsReader
      : ConfigReader[List[CreateStreamRequest]] = {
    ConfigReader.fromString { s =>
      s.split(',')
        .toList
        .map(_.split(':').toList)
        .traverse {
          case name :: count :: Nil if name.nonEmpty && count.nonEmpty =>
            count.toIntOption.map(CreateStreamRequest(_, StreamName(name)))
          case _ => none
        }
        .toRight(
          CannotConvert(
            s,
            "Vector[CreateStreamRequest]",
            "Invalid format. Expected: \"<String>:<Int>,<String>:<Int>,...\""
          )
        )
    }
  }

  def read: IO[CacheConfig] =
    ConfigSource.resources("cache.conf").loadF[IO, CacheConfig](blocker)
}
