package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import io.circe.{Encoder, Json}
import io.circe.derivation._
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.module.enumeratum._
import pureconfig.module.circe._
import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.instances.circe._
import kinesis.mock.models._

final case class CacheConfig(
    initializeStreams: Option[Map[AwsRegion, List[CreateStreamRequest]]],
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
      : ConfigReader[Map[AwsRegion, List[CreateStreamRequest]]] =
    ConfigReader[Json].emap { json =>
      json
        .as[Map[AwsRegion, List[CreateStreamRequest]]]
        .leftMap(e =>
          CannotConvert(
            json.noSpaces,
            s"Map[AwRegion, List[CreateStreamRequest]]",
            e.show
          )
        )
    }

  ConfigReader.fromString { s =>
    s.split(',')
      .toList
      .map(_.split(':').toList)
      .traverse {
        case region :: name :: count :: Nil
            if name.nonEmpty && count.nonEmpty &&
              AwsRegion.withNameOption(region).nonEmpty =>
          count.toIntOption.map(c =>
            AwsRegion
              .withName(region) -> CreateStreamRequest(c, StreamName(name))
          )
        case _ => none
      }
      .toRight(
        CannotConvert(
          s,
          "Vector[CreateStreamRequest]",
          "Invalid format. Expected: \"<String>:<Int>,<String>:<Int>,...\""
        )
      )
      .map(_.groupMap(_._1)(_._2))
  }

  def read: IO[CacheConfig] =
    ConfigSource.resources("cache.conf").loadF[IO, CacheConfig]()
}
