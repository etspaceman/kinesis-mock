package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DisableEnhancedMonitoringRequestTests extends munit.ScalaCheckSuite {
  property("It should disable enhanced monitoring")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val (streams, _) =
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

      val req =
        DisableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          streamName
        )
      val res = req.disableEnhancedMonitoring(updated)
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
