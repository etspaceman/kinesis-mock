package kinesis.mock

import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class KinesisMockFunctionalTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  val streamName = streamNameGen.one.streamName

  clientsFixture.test("It should create a stream") { client =>
    client
      .createStream(
        CreateStreamRequest
          .builder()
          .streamName(streamName)
          .shardCount(1)
          .build()
      )
      .toIO
      .attempt
      .map { res =>
        assert(res.isRight, res)
      }
  }
}
