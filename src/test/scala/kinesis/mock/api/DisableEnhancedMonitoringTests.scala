package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DisableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should disable enhanced monitoring")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val updated = streams.findAndUpdateStream(streamName)(stream =>
        stream.copy(enhancedMonitoring =
          List(
            ShardLevelMetrics(
              List(
                ShardLevelMetric.IncomingBytes,
                ShardLevelMetric.IncomingRecords,
                ShardLevelMetric.OutgoingBytes,
                ShardLevelMetric.OutgoingRecords,
                ShardLevelMetric.WriteProvisionedThroughputExceeded,
                ShardLevelMetric.ReadProvisionedThroughputExceeded,
                ShardLevelMetric.IteratorAgeMilliseconds
              )
            )
          )
        )
      )

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DisableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          streamName
        )
        res <- req.disableEnhancedMonitoring(streamsRef)
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
