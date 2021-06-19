package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should enable enhanced monitoring")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = EnableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          streamName
        )
        res <- req.enableEnhancedMonitoring(streamsRef)
        s <- streamsRef.get
        updatedMetrics = s.streams
          .get(streamName)
          .map(_.enhancedMonitoring.flatMap(_.shardLevelMetrics))

      } yield assert(
        res.isRight && res.exists { case response =>
          updatedMetrics.contains(response.desiredShardLevelMetrics)
        },
        s"req: $req\nres: $res\nupdatedMetrics: $updatedMetrics"
      )
  })
}
