package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should enable enhanced monitoring")(PropF.forAllF {
    (
        streamArn: StreamArn,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = EnableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          streamArn.streamName
        )
        res <- req.enableEnhancedMonitoring(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
        updatedMetrics = s.streams
          .get(streamArn)
          .map(_.enhancedMonitoring.flatMap(_.shardLevelMetrics))

      } yield assert(
        res.isRight && res.exists { case response =>
          updatedMetrics.contains(response.desiredShardLevelMetrics)
        },
        s"req: $req\nres: $res\nupdatedMetrics: $updatedMetrics"
      )
  })
}
