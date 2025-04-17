package kinesis.mock

import kinesis.mock.syntax.javaFuture.*

class ListStreamsTests extends AwsFunctionalTests:

  fixture.test("It should list streams") { resources =>
    for res <- resources.kinesisClient.listStreams().toIO
    yield assert(
      !res.streamNames().isEmpty,
      s"$res"
    )
  }

  fixture.test("It should list all initialized streams") { resources =>
    for res <- resources.defaultRegionKinesisClient.listStreams().toIO
    yield assert(
      initializedStreams.forall { case (name, _) =>
        res.streamNames().contains(name)
      },
      s"$res"
    )
  }
