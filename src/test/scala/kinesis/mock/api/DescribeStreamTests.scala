package kinesis.mock
package api

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should describe a stream")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, None, streamArn.streamName)
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
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
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(2, streamArn)

      val limit = Some(1)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(None, limit, streamArn.streamName)
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
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
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(4, streamArn)

      val exclusiveStartShardId = streams.streams
        .get(streamArn)
        .flatMap(_.shards.headOption.map(_._1.shardId.shardId))

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DescribeStreamRequest(
          exclusiveStartShardId,
          None,
          streamArn.streamName
        )
        res <- req.describeStream(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        streamDescription = streams.streams
          .get(streamArn)
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
    (
        req: DescribeStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.describeStream(streamsRef, awsRegion, awsAccountId)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
