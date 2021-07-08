package kinesis.mock
package api

import cats.effect._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class PutRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should put records")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        initReq: PutRecordsRequest
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val active =
        streams.findAndUpdateStream(streamName)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req = initReq.copy(
        streamName = streamName
      )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.putRecords(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.shards.values.toVector.flatten.count { rec =>
            req.records.map(_.data).exists(_.sameElements(rec.data))
          } == initReq.records.length
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when the stream is not active or updating")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId,
          initReq: PutRecordsRequest
      ) =>
        val streams =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val req = initReq.copy(
          streamName = streamName
        )

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          res <- req.putRecords(streamsRef)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject when the shard is closed")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId,
          initReq: PutRecordsRequest
      ) =>
        val streams =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val updated = streams.findAndUpdateStream(streamName)(s =>
          s.copy(shards = s.shards.map { case (shard, recs) =>
            shard.copy(sequenceNumberRange =
              shard.sequenceNumberRange.copy(endingSequenceNumber =
                Some(SequenceNumber.shardEnd)
              )
            ) -> recs
          })
        )

        val req = initReq.copy(
          streamName = streamName
        )

        for {
          streamsRef <- Ref.of[IO, Streams](updated)
          res <- req.putRecords(streamsRef)
        } yield assert(
          res.isLeft,
          s"req: $req\nres: $res"
        )
    }
  )

}
