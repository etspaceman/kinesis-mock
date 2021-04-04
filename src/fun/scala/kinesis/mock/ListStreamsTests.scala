package kinesis.mock

import kinesis.mock.syntax.javaFuture._

class ListStreamsTests extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should list streams") { case resources =>
    for {
      res <- resources.kinesisClient.listStreams().toIO
    } yield assert(
      res.streamNames().size == 1,
      s"$res"
    )
  }
}
