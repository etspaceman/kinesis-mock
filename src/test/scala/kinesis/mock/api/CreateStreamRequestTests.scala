package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class CreateStreamRequestTests extends munit.ScalaCheckSuite {
  property("It should create a stream")(forAll {
    (
        req: CreateStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      val res =
        req.createStream(streams, req.shardCount, awsRegion, awsAccountId)

      (res.isValid && res.exists { case (s, _) =>
        s.streams.get(req.streamName).exists { stream =>
          stream.shards.size == req.shardCount
        }
      }) :| s"req: $req\nres: $res"
  })

  property("It should reject if the shardCount exceeds the shardLimit")(forAll {
    (
        req: CreateStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      val res =
        req.createStream(streams, req.shardCount - 1, awsRegion, awsAccountId)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
