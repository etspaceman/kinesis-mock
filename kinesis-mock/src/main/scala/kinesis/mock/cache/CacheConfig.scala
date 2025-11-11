/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package cache

import scala.concurrent.duration.*

import cats.implicits.*
import ciris.*
import io.circe.Encoder

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.instances.circe.given
import kinesis.mock.models.*

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
    updateStreamModeDuration: FiniteDuration,
    shardLimit: Int,
    awsAccountId: AwsAccountId,
    awsRegion: AwsRegion,
    persistConfig: PersistConfig,
    onDemandStreamCountLimit: Int,
    logLevel: ConsoleLogger.LogLevel
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
    updateStreamModeDuration: FiniteDuration,
    shardLimit: Int,
    awsAccountId: AwsAccountId,
    awsRegion: AwsRegion,
    persistConfig: PersistConfig,
    onDemandStreamCountLimit: Int,
    logLevel: ConsoleLogger.LogLevel
)

object CacheConfig:
  private def readStep1: ConfigValue[Effect, CacheConfigStep1] = for
    initializeStreamsStr <- env("INITIALIZE_STREAMS").option
    createStreamDuration <- env("CREATE_STREAM_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    deleteStreamDuration <- env("DELETE_STREAM_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    registerStreamConsumerDuration <- env("REGISTER_STREAM_CONSUMER_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    deregisterStreamConsumerDuration <- env("START_STREAM_ENCRYPTION_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    startStreamEncryptionDuration <- env("STOP_STREAM_ENCRYPTION_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    stopStreamEncryptionDuration <- env("DEREGISTER_STREAM_CONSUMER_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    mergeShardsDuration <- env("MERGE_SHARDS_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    splitShardDuration <- env("SPLIT_SHARD_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    updateShardCountDuration <- env("UPDATE_SHARD_COUNT_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    updateStreamModeDuration <- env("UPDATE_STREAM_MODE_DURATION")
      .default("500ms")
      .as[FiniteDuration]
    shardLimit <- env("SHARD_LIMIT").default("50").as[Int]
    awsAccountId <- env("AWS_ACCOUNT_ID")
      .default("000000000000")
      .as[AwsAccountId]
    awsRegion <- env("AWS_REGION").default("us-east-1").as[AwsRegion]
    persistConfig <- PersistConfig.read
    onDemandStreamCountLimit <- env("ON_DEMAND_STREAM_COUNT_LIMIT")
      .default("10")
      .as[Int]
    logLevel <- ConsoleLogger.LogLevel.read
  yield CacheConfigStep1(
    initializeStreamsStr,
    createStreamDuration,
    deleteStreamDuration,
    registerStreamConsumerDuration,
    deregisterStreamConsumerDuration,
    startStreamEncryptionDuration,
    stopStreamEncryptionDuration,
    mergeShardsDuration,
    splitShardDuration,
    updateShardCountDuration,
    updateStreamModeDuration,
    shardLimit,
    awsAccountId,
    awsRegion,
    persistConfig,
    onDemandStreamCountLimit,
    logLevel
  )

  def read: ConfigValue[Effect, CacheConfig] = readStep1.flatMap(step1 =>
    step1.initializeStreamsStr match
      case None =>
        ConfigValue.default(
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
            step1.updateStreamModeDuration,
            step1.shardLimit,
            step1.awsAccountId,
            step1.awsRegion,
            step1.persistConfig,
            step1.onDemandStreamCountLimit,
            step1.logLevel
          )
        )

      case Some(s) =>
        initializeStreamsReader(step1.awsRegion, s).fold(
          ConfigValue.failed(_),
          initializeStreams =>
            ConfigValue.default(
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
                step1.updateStreamModeDuration,
                step1.shardLimit,
                step1.awsAccountId,
                step1.awsRegion,
                step1.persistConfig,
                step1.onDemandStreamCountLimit,
                step1.logLevel
              )
            )
        )
  )

  given Encoder[CacheConfig] =
    Encoder.forProduct16(
      "initializeStreams",
      "createStreamDuration",
      "deleteStreamDuration",
      "registerStreamConsumerDuration",
      "deregisterStreamConsumerDuration",
      "startStreamEncryptionDuration",
      "stopStreamEncryptionDuration",
      "mergeShardsDuration",
      "splitShardDuration",
      "updateShardCountDuration",
      "shardLimit",
      "awsAccountId",
      "awsRegion",
      "persistConfig",
      "onDemandStreamCountLimit",
      "logLevel"
    )(x =>
      (
        x.initializeStreams,
        x.createStreamDuration,
        x.deleteStreamDuration,
        x.registerStreamConsumerDuration,
        x.deregisterStreamConsumerDuration,
        x.startStreamEncryptionDuration,
        x.stopStreamEncryptionDuration,
        x.mergeShardsDuration,
        x.splitShardDuration,
        x.updateShardCountDuration,
        x.shardLimit,
        x.awsAccountId,
        x.awsRegion,
        x.persistConfig,
        x.onDemandStreamCountLimit,
        x.logLevel
      )
    )

  def initializeStreamsReader(
      defaultRegion: AwsRegion,
      s: String
  ): Either[ConfigError, Map[AwsRegion, List[CreateStreamRequest]]] =
    s.split(',')
      .toList
      .map(_.split(':').toList)
      .traverse {
        case name :: Nil if name.nonEmpty =>
          Some(
            defaultRegion -> CreateStreamRequest(None, None, StreamName(name))
          )
        case name :: count :: Nil if name.nonEmpty =>
          if count.isEmpty then
            Some(
              defaultRegion -> CreateStreamRequest(None, None, StreamName(name))
            )
          else
            count.toIntOption.map(x =>
              defaultRegion -> CreateStreamRequest(
                Some(x),
                None,
                StreamName(name)
              )
            )
        case name :: count :: region :: Nil if name.nonEmpty =>
          val regionOrDefault =
            AwsRegion.withNameOption(region).getOrElse(defaultRegion)
          if count.isEmpty then
            Some(
              regionOrDefault -> CreateStreamRequest(
                None,
                None,
                StreamName(name)
              )
            )
          else
            count.toIntOption.map(x =>
              regionOrDefault -> CreateStreamRequest(
                Some(x),
                None,
                StreamName(name)
              )
            )
        case _ => none
      }
      .map(_.groupMap(_._1)(_._2))
      .toRight(
        ConfigError(
          "Invalid format for INITIALIZE_STREAMS. Expected: \"<String>:<Int>:<String>,<String>:<Int>:<String>,...\""
        )
      )
