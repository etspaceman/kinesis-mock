package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DisableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should disable enhanced monitoring")(PropF.forAllF {
    (
        streamArn: StreamArn,
        shardLevelMetrics: ShardLevelMetrics
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val updated = streams.findAndUpdateStream(streamArn)(stream =>
        stream.copy(enhancedMonitoring =
          Some(
            Vector(
              ShardLevelMetrics(
                Vector(
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
      )

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = DisableEnhancedMonitoringRequest(
          shardLevelMetrics.shardLevelMetrics,
          None,
          Some(streamArn)
        )
        res <- req.disableEnhancedMonitoring(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
        updatedMetrics = s.streams
          .get(streamArn)
          .map(_.enhancedMonitoring.map(_.flatMap(_.shardLevelMetrics)))

      } yield assert(
        res.isRight && res.exists { case response =>
          updatedMetrics.contains(response.desiredShardLevelMetrics)
        },
        s"req: $req\nres: $res\nupdatedMetrics: $updatedMetrics"
      )
  })
}
