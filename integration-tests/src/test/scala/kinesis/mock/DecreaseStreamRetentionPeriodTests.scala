package kinesis.mock

import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class DecreaseStreamRetentionPeriodTests extends AwsFunctionalTests {

  fixture().test("It should decrease the stream retention period") { resources =>
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
      res.streamDescriptionSummary().retentionPeriodHours().intValue == 24,
      res
    )
  }
}
