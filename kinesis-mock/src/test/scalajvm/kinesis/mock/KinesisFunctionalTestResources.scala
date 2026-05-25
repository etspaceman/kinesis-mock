/*
 * Copyright 2021-2026 io.github.etspaceman
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

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient

import kinesis.mock.cache.CacheConfig
import kinesis.mock.models.{AwsRegion, StreamName}

case class KinesisFunctionalTestResources(
    kinesisClient: KinesisAsyncClient,
    defaultRegionKinesisClient: KinesisAsyncClient,
    cacheConfig: CacheConfig,
    streamName: StreamName,
    testConfig: FunctionalTestConfig,
    httpProtocol: String,
    logger: SelfAwareStructuredLogger[IO],
    awsRegion: AwsRegion
):
  val sdkRegion: Region = Region.of(awsRegion.entryName)
