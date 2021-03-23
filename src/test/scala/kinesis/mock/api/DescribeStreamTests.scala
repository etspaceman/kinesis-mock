package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamTests extends munit.ScalaCheckSuite {
  property("It should describe a stream")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val req =
        DescribeStreamRequest(None, None, streamName)
      val res = req.describeStream(streams)
      val streamDescription = streams.streams
        .get(streamName)
        .map(s => StreamDescription.fromStreamData(s, None, None))

      (res.isValid && res.exists { case response =>
        streamDescription.contains(response.streamDescription)
      }) :| s"req: $req\nres: $res"
  })

  property("It should limit the shard count")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(2, streamName, awsRegion, awsAccountId)

      val limit = Some(1)
      val req =
        DescribeStreamRequest(None, limit, streamName)
      val res = req.describeStream(streams)
      val streamDescription = streams.streams
        .get(streamName)
        .map(s => StreamDescription.fromStreamData(s, None, limit))

      (res.isValid && res.exists { case response =>
        streamDescription.contains(
          response.streamDescription
        ) && response.streamDescription.shards.size == 1
      }) :| s"req: $req\nres: $res"
  })

  property("It should start after a shardId")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(4, streamName, awsRegion, awsAccountId)

      val exclusiveStartShardId = streams.streams
        .get(streamName)
        .flatMap(_.shards.headOption.map(_._1.shardId.shardId))

      val req =
        DescribeStreamRequest(exclusiveStartShardId, None, streamName)
      val res = req.describeStream(streams)
      val streamDescription = streams.streams
        .get(streamName)
        .map(s =>
          StreamDescription.fromStreamData(s, exclusiveStartShardId, None)
        )

      (res.isValid && res.exists { case response =>
        streamDescription.contains(
          response.streamDescription
        ) &&
          response.streamDescription.shards.size == 3 &&
          response.streamDescription.shards
            .find(x => exclusiveStartShardId.contains(x.shardId))
            .isEmpty
      }) :| s"req: $req\nres: $res"
  })

  property("It should reject if the stream does not exist")(forAll {
    req: DescribeStreamRequest =>
      val streams = Streams.empty

      val res = req.describeStream(streams)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
