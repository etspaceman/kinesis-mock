package kinesis.mock

import scala.concurrent.duration._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class UpdateStreamModeTests extends AwsFunctionalTests {

  fixture.test("It should update the stream mode") { resources =>
    for {
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
    } yield assert(
      res.streamDescriptionSummary().streamModeDetails() == streamModeDetails,
      res
    )
  }
}
