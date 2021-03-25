package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests extends munit.ScalaCheckSuite {
  property("It should enable enhanced monitoring")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val req =
        EnableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          streamName
        )
      val res = req.enableEnhancedMonitoring(streams)
      val updatedMetrics = res.toOption.flatMap { case (s, _) =>
        s.streams
          .get(streamName)
          .map(_.enhancedMonitoring.flatMap(_.shardLevelMetrics))
      }

      (res.isValid && res.exists { case (_, response) =>
        updatedMetrics.contains(response.desiredShardLevelMetrics)
      }) :| s"req: $req\nres: $res\nupdatedMetrics: $updatedMetrics"
  })
}
