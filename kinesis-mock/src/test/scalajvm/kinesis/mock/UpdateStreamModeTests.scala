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

import scala.concurrent.duration.*

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class UpdateStreamModeTests extends AwsFunctionalTests:

  fixture().test("It should update the stream mode") { resources =>
    for
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      streamModeDetails = StreamModeDetails
        .builder()
        .streamMode(StreamMode.ON_DEMAND)
        .build()
      _ <- resources.kinesisClient
        .updateStreamMode(
          UpdateStreamModeRequest
            .builder()
            .streamARN(streamArn)
            .streamModeDetails(streamModeDetails)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.updateStreamModeDuration.plus(400.millis)
      )
      res <- describeStreamSummary(resources)
    yield assert(
      res.streamDescriptionSummary().streamModeDetails() == streamModeDetails,
      res
    )
  }
