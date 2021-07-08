package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamTests extends KinesisMockSuite {
  test("It should describe a stream")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, None, streamName)
        res <- req.describeStream(streamsRef)
        streamDescription = streams.streams
          .get(streamName)
          .map(s => StreamDescription.fromStreamData(s, None, None))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(response.streamDescription)
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should limit the shard count")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(2, streamName, awsRegion, awsAccountId)

      val limit = Some(1)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, limit, streamName)
        res <- req.describeStream(streamsRef)
        streamDescription = streams.streams
          .get(streamName)
          .map(s => StreamDescription.fromStreamData(s, None, limit))
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(
            response.streamDescription
          ) && response.streamDescription.shards.size == 1
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should start after a shardId")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(4, streamName, awsRegion, awsAccountId)

      val exclusiveStartShardId = streams.streams
        .get(streamName)
        .flatMap(_.shards.headOption.map(_._1.shardId.shardId))

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(exclusiveStartShardId, None, streamName)
        res <- req.describeStream(streamsRef)
        streamDescription = streams.streams
          .get(streamName)
          .map(s =>
            StreamDescription.fromStreamData(s, exclusiveStartShardId, None)
          )
      } yield assert(
        res.isRight && res.exists { response =>
          streamDescription.contains(response.streamDescription) &&
          response.streamDescription.shards.size == 3 &&
          !response.streamDescription.shards.exists(x =>
            exclusiveStartShardId.contains(x.shardId)
          )
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream does not exist")(PropF.forAllF {
    req: DescribeStreamRequest =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStream(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
