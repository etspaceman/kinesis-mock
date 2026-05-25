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

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class ListTagsForStreamTests extends AwsFunctionalTests:

  fixture().test("It should list tags for a stream") { resources =>
    for
      tags <- IO(tagsGen.one)
      _ <- resources.kinesisClient
        .addTagsToStream(
          AddTagsToStreamRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .tags(tags.tags.asJava)
            .build()
        )
        .toIO
      res <- listTagsForStream(resources)
    yield assert(
      Map.from(
        res.tags().asScala.map(tag => tag.key() -> tag.value())
      ) == tags.tags,
      res
    )
  }
