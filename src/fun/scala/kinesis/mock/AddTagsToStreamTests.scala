package kinesis.mock

import scala.jdk.CollectionConverters._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class AddTagsToStreamTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  val streamName = streamNameGen.one.streamName

  fixture.test("It should add tags to a stream") { case resources =>
    for {
      tags <- IO(tagsGen.one)
      res <- resources.kinesisClient
        .addTagsToStream(
          AddTagsToStreamRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .tags(tags.tags.asJava)
            .build()
        )
        .toIO
        .attempt
    } yield assert(res.isRight, res)
  }
}
