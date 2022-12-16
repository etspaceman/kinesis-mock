package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant

import cats.effect.{IO, Ref}
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class ListShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list shards when provided a streamName")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          None,
          None,
          None,
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          streams.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should paginate properly")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          Some(50),
          None,
          None,
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        paginatedRes <- res
          .traverse(result =>
            ListShardsRequest(
              None,
              Some(50),
              result.nextToken,
              None,
              None,
              None
            )
              .listShards(
                streamsRef,
                streamArn.awsRegion,
                streamArn.awsAccountId
              )
          )
          .map(_.flatMap(identity))
      } yield assert(
        res.isRight && paginatedRes.isRight && res.exists { response =>
          streams.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .take(50)
              .map(ShardSummary.fromShard) == response.shards
          }
        } && paginatedRes.exists { response =>
          streams.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(50)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.shards.length)}\n" +
          s"paginatedResCount: ${paginatedRes.map(_.shards.length)}}"
      )
  })

  test(
    "It should list shards when provided a streamName and exclusiveStartShardId"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val exclusiveStartShardId = ShardId.create(10)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          Some(exclusiveStartShardId.shardId),
          None,
          None,
          None,
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          streams.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(89)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should list shards when filtered by AT_LATEST")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val updated = streams.findAndUpdateStream(streamArn) { s =>
        val shards = s.shards.toList
        s.copy(
          shards = SortedMap.from(shards.takeRight(95) ++ shards.take(5).map {
            case (shard, recs) =>
              shard.copy(sequenceNumberRange =
                shard.sequenceNumberRange.copy(
                  Some(SequenceNumber.shardEnd),
                  shard.sequenceNumberRange.startingSequenceNumber
                )
              ) -> recs
          })
        )
      }

      val filter = ShardFilter(None, None, ShardFilterType.AT_LATEST)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(filter),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          updated.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(95)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"res: ${res.map(_.shards.map(_.shardId))}\n" +
          s"current: ${updated.streams(streamArn).shards.keys.toVector.takeRight(5).map(_.shardId)}\n" +
          s"closedShardsLen: ${updated.streams.get(streamArn).map(_.shards.keys.filterNot(_.isOpen).size)}"
      )
  })

  test("It should list shards when filtered by AT_TRIM_HORIZON")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val updated = streams.findAndUpdateStream(streamArn) { s =>
        val shards = s.shards.toList
        s.copy(
          shards = SortedMap.from(shards.takeRight(95) ++ shards.take(5).map {
            case (shard, recs) =>
              shard.copy(sequenceNumberRange =
                shard.sequenceNumberRange.copy(
                  Some(SequenceNumber.shardEnd),
                  shard.sequenceNumberRange.startingSequenceNumber
                )
              ) -> recs
          })
        )
      }

      val filter = ShardFilter(None, None, ShardFilterType.AT_TRIM_HORIZON)

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(filter),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          updated.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"res: ${res.map(_.shards.length)}\n" +
          s"closedShardsLen: ${updated.streams.get(streamArn).map(_.shards.keys.filterNot(_.isOpen).size)}"
      )
  })

  test("It should list shards when filtered by FROM_TRIM_HORIZON")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        val streams =
          Streams.empty.addStream(100, streamArn, None)

        val updated = streams.findAndUpdateStream(streamArn) { s =>
          val shards = s.shards.toList
          s.copy(shards =
            SortedMap.from(shards.takeRight(95) ++ shards.take(5).map {
              case (shard, recs) =>
                shard.copy(
                  sequenceNumberRange = shard.sequenceNumberRange.copy(
                    Some(SequenceNumber.shardEnd),
                    shard.sequenceNumberRange.startingSequenceNumber
                  ),
                  closedTimestamp = Some(
                    Instant.now().minusSeconds(s.retentionPeriod.toSeconds + 2)
                  )
                ) -> recs
            })
          )
        }

        val filter = ShardFilter(None, None, ShardFilterType.FROM_TRIM_HORIZON)

        for {
          streamsRef <- Ref.of[IO, Streams](updated)
          req = ListShardsRequest(
            None,
            None,
            None,
            Some(filter),
            None,
            Some(streamArn.streamName)
          )
          res <- req.listShards(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(
          res.isRight && res.exists { response =>
            updated.streams.get(streamArn).exists { s =>
              s.shards.keys.toVector
                .takeRight(95)
                .map(ShardSummary.fromShard) == response.shards
            }
          },
          s"req: $req\n" +
            s"res: ${res.map(_.shards.length)}\n" +
            s"closedShardsLen: ${updated.streams.get(streamArn).map(_.shards.keys.filterNot(_.isOpen).size)}"
        )
    }
  )

  test("It should list shards when filtered by AFTER_SHARD_ID")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val shards = streams.streams(streamArn).shards.keys.toVector
      val shardId = shards(4).shardId
      val filter =
        ShardFilter(Some(shardId.shardId), None, ShardFilterType.AFTER_SHARD_ID)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(filter),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          streams.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(95)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"resLen: ${res.map(_.shards.length)}\n" +
          s"resultingHead: ${res.map(_.shards.head).fold(_.toString, _.toString)}\n" +
          s"expectResHead: ${streams.streams.get(streamArn).map(_.shards.keys.toVector(45)).get}\n" +
          s"resultingLast: ${res.map(_.shards.last).fold(_.toString, _.toString)}\n" +
          s"expectResLast: ${streams.streams.get(streamArn).map(_.shards.keys.toVector.last).get}"
      )
  })

  test("It should list shards when filtered by FROM_TIMESTAMP")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val requestTimestamp = Instant.parse("2021-01-30T00:00:00.00Z")
      val updated = streams.findAndUpdateStream(streamArn) { s =>
        val shards = s.shards.toVector
        s.copy(
          streamCreationTimestamp = requestTimestamp.minusSeconds(600),
          shards = SortedMap.from(shards.takeRight(90) ++ shards.take(5).map {
            case (shard, recs) =>
              shard.copy(
                sequenceNumberRange = shard.sequenceNumberRange.copy(
                  Some(SequenceNumber.shardEnd),
                  shard.sequenceNumberRange.startingSequenceNumber
                ),
                closedTimestamp = Some(
                  requestTimestamp.minusSeconds(5)
                )
              ) -> recs
          } ++ shards.slice(5, 10).map { case (shard, recs) =>
            shard.copy(
              sequenceNumberRange = shard.sequenceNumberRange.copy(
                Some(SequenceNumber.shardEnd),
                shard.sequenceNumberRange.startingSequenceNumber
              ),
              closedTimestamp = Some(
                requestTimestamp.plusSeconds(5)
              )
            ) -> recs
          })
        )
      }

      val filter = ShardFilter(
        None,
        Some(requestTimestamp),
        ShardFilterType.FROM_TIMESTAMP
      )

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(filter),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          updated.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(95)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"res: ${res.map(_.shards.length)}\n" +
          s"closedShardsLen: ${updated.streams.get(streamArn).map(_.shards.keys.filterNot(_.isOpen).size)}"
      )
  })

  test("It should list shards when filtered by AT_TIMESTAMP")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      val requestTimestamp = Instant.parse("2021-01-30T00:00:00.00Z")
      val updated = streams.findAndUpdateStream(streamArn) { s =>
        val shards = s.shards.toVector
        s.copy(
          streamCreationTimestamp = requestTimestamp.minusSeconds(600),
          shards =
            SortedMap.from(shards.takeRight(90).map { case (shard, data) =>
              shard.copy(createdAtTimestamp = requestTimestamp) -> data
            } ++ shards.take(5).map { case (shard, recs) =>
              shard.copy(
                sequenceNumberRange = shard.sequenceNumberRange.copy(
                  Some(SequenceNumber.shardEnd),
                  shard.sequenceNumberRange.startingSequenceNumber
                ),
                closedTimestamp = Some(
                  requestTimestamp.minusSeconds(5)
                )
              ) -> recs

            } ++ shards.slice(5, 10).map { case (shard, recs) =>
              shard.copy(
                sequenceNumberRange = shard.sequenceNumberRange.copy(
                  Some(SequenceNumber.shardEnd),
                  shard.sequenceNumberRange.startingSequenceNumber
                ),
                closedTimestamp = Some(
                  requestTimestamp.plusSeconds(5)
                ),
                createdAtTimestamp = requestTimestamp
              ) -> recs
            })
        )
      }

      val filter = ShardFilter(
        None,
        Some(requestTimestamp),
        ShardFilterType.AT_TIMESTAMP
      )

      for {
        streamsRef <- Ref.of[IO, Streams](updated)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(filter),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          updated.streams.get(streamArn).exists { s =>
            s.shards.keys.toVector
              .takeRight(95)
              .map(ShardSummary.fromShard) == response.shards
          }
        },
        s"req: $req\n" +
          s"res: ${res.map(_.shards.length)}\n" +
          s"closedShardsLen: ${updated.streams.get(streamArn).map(_.shards.keys.filterNot(_.isOpen).size)}"
      )
  })

  test(
    "It should reject when given a shard-filter of type AT_TIMESTAMP without a timestamp"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(ShardFilter(None, None, ShardFilterType.AT_TIMESTAMP)),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject when given a shard-filter of type FROM_TIMESTAMP without a timestamp"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(ShardFilter(None, None, ShardFilterType.FROM_TIMESTAMP)),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test(
    "It should reject when given a shard-filter of type AFTER_SHARD_ID without a shard-id"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(100, streamArn, None)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListShardsRequest(
          None,
          None,
          None,
          Some(ShardFilter(None, None, ShardFilterType.AFTER_SHARD_ID)),
          None,
          Some(streamArn.streamName)
        )
        res <- req.listShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
