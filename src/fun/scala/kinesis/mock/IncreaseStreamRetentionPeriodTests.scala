package kinesis.mock

import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class IncreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should increase the stream retention period") {
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
        res <- describeStreamSummary(resources)
      } yield assert(
        res.streamDescriptionSummary().retentionPeriodHours().intValue == 48,
        res
      )
  }
}
