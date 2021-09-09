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
) {
  val initializeStreams: Option[Map[AwsRegion, List[CreateStreamRequest]]] =
    ConfigSource
      .resources("cache.conf")
      .loadOrThrow[Option[Map[AwsRegion, List[CreateStreamRequest]]]](
        implicitly,
        ConfigReader.optionReader(CacheConfig.initializeStreamsReader(awsRegion))
      )
}

object CacheConfig {
  implicit val cacheConfigReader: ConfigReader[CacheConfig] = deriveReader
  implicit val cacheConfigCirceEncoder: Encoder[CacheConfig] = deriveEncoder

  def initializeStreamsReader(
      defaultRegion: AwsRegion
  ): ConfigReader[Map[AwsRegion, List[CreateStreamRequest]]] = {
    ConfigReader.fromString { s =>
      s.split(',')
        .toList
        .map(_.split(':').toList)
        .traverse {
          case name :: count :: Nil if name.nonEmpty && count.nonEmpty =>
            count.toIntOption.map(x =>
              defaultRegion -> CreateStreamRequest(x, StreamName(name))
            )
          case name :: count :: region :: Nil
              if name.nonEmpty && count.nonEmpty =>
            count.toIntOption.map(x =>
              AwsRegion
                .withNameOption(region)
                .getOrElse(defaultRegion) -> CreateStreamRequest(
                x,
                StreamName(name)
              )
            )
          case _ => none
        }
        .map(_.groupMap(_._1)(_._2))
        .toRight(
          CannotConvert(
            s,
            "Map[AwsRegion, List[CreateStreamRequest]]",
            "Invalid format. Expected: \"<String>:<Int>:<String>,<String>:<Int>:<String>,...\""
          )
        )
    }
  }

  def read: IO[CacheConfig] =
    ConfigSource.resources("cache.conf").loadF[IO, CacheConfig]()
}
