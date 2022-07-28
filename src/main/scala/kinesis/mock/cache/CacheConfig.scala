package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import io.circe.Encoder
import io.circe.derivation._
import pureconfig._
import pureconfig.error.{CannotConvert, FailureReason}
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._
import pureconfig.module.enumeratum._

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

final case class CacheConfigStep1(
    initializeStreamsStr: Option[String],
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
  implicit val cacheConfigStep1Reader: ConfigReader[CacheConfigStep1] =
    deriveReader
  implicit val cacheConfigReader: ConfigReader[CacheConfig] =
    cacheConfigStep1Reader
      .emap(step1 =>
        step1.initializeStreamsStr match {
          case None =>
            Right(
              CacheConfig(
                None,
                step1.createStreamDuration,
                step1.deleteStreamDuration,
                step1.registerStreamConsumerDuration,
                step1.deregisterStreamConsumerDuration,
                step1.startStreamEncryptionDuration,
                step1.stopStreamEncryptionDuration,
                step1.mergeShardsDuration,
                step1.splitShardDuration,
                step1.updateShardCountDuration,
                step1.shardLimit,
                step1.awsAccountId,
                step1.awsRegion,
                step1.persistConfig
              )
            )

          case Some(s) =>
            initializeStreamsReader(step1.awsRegion, s).map {
              initializeStreams =>
                CacheConfig(
                  Some(initializeStreams),
                  step1.createStreamDuration,
                  step1.deleteStreamDuration,
                  step1.registerStreamConsumerDuration,
                  step1.deregisterStreamConsumerDuration,
                  step1.startStreamEncryptionDuration,
                  step1.stopStreamEncryptionDuration,
                  step1.mergeShardsDuration,
                  step1.splitShardDuration,
                  step1.updateShardCountDuration,
                  step1.shardLimit,
                  step1.awsAccountId,
                  step1.awsRegion,
                  step1.persistConfig
                )
            }
        }
      )

  implicit val cacheConfigCirceEncoder: Encoder[CacheConfig] = deriveEncoder

  def initializeStreamsReader(
      defaultRegion: AwsRegion,
      s: String
  ): Either[FailureReason, Map[AwsRegion, List[CreateStreamRequest]]] = {
    s.split(',')
      .toList
      .map(_.split(':').toList)
      .traverse {
        case name :: count :: Nil if name.nonEmpty =>
          if (count.isEmpty) Some(defaultRegion -> CreateStreamRequest(None, StreamName(name)))
          else count.toIntOption.map(x => defaultRegion -> CreateStreamRequest(Some(x), StreamName(name)))
        case name :: count :: region :: Nil
            if name.nonEmpty =>
          val regionOrDefault = AwsRegion.withNameOption(region).getOrElse(defaultRegion)
          if (count.isEmpty) Some(regionOrDefault -> CreateStreamRequest(None, StreamName(name)))
          else count.toIntOption.map(x => regionOrDefault -> CreateStreamRequest(Some(x), StreamName(name)))
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

  def read: IO[CacheConfig] =
    ConfigSource.resources("cache.conf").loadF[IO, CacheConfig]()
}
