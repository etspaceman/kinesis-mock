package kinesis.mock

import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  val streamName = streamNameGen.one.streamName

  fixture.test("It should decrease the stream retention period") {
    case resources =>
      for {
        _ <- resources.kinesisClient
          .increaseStreamRetentionPeriod(
            IncreaseStreamRetentionPeriodRequest
              .builder()
              .streamName(resources.streamName.streamName)
              .retentionPeriodHours(48)
              .build()
          )
          .toIO
        _ <- resources.kinesisClient
          .decreaseStreamRetentionPeriod(
            DecreaseStreamRetentionPeriodRequest
              .builder()
              .streamName(resources.streamName.streamName)
              .retentionPeriodHours(24)
              .build()
          )
          .toIO
        res <- describeStreamSummary(resources)
      } yield assert(
        res.streamDescriptionSummary().retentionPeriodHours() == 24,
        res
      )
  }
}
