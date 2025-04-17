package kinesis.mock

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class AddTagsToStreamTests extends AwsFunctionalTests:

  fixture.test("It should add tags to a stream") { resources =>
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
