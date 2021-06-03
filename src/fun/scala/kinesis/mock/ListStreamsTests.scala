package kinesis.mock

import kinesis.mock.syntax.javaFuture._

class ListStreamsTests extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should list streams") { resources =>
    for {
      res <- resources.kinesisClient.listStreams().toIO
    } yield assert(
      !res.streamNames().isEmpty,
      s"$res"
    )
  }

  fixture.test("It should list all initialized streams") { resources =>
    for {
      res <- resources.kinesisClient.listStreams().toIO
    } yield assert(
      initializedStreams.forall { case (name, _) =>
        res.streamNames().contains(name)
      },
      s"$res"
    )
  }
}
