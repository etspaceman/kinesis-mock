package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should list streams")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streamNames = Gen
        .listOfN(10, Arbitrary.arbitrary[StreamName])
        .suchThat(x =>
          x.groupBy(_.streamName)
            .collect { case (_, y) if y.length > 1 => y }
            .isEmpty
        )
        .one
        .sorted

      val streams = streamNames.foldLeft(Streams.empty) {
        case (streams, streamName) =>
          streams.addStream(1, streamName, awsRegion, awsAccountId)._1
      }

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(None, None)
        res <- req.listStreams(streamsRef)
      } yield assert(
        res.isValid && res.exists { response =>
          streamNames == response.streamNames
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })

  test("It should filter by exclusiveStartStreamName")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streamNames = Gen
        .listOfN(10, Arbitrary.arbitrary[StreamName])
        .suchThat(x =>
          x.groupBy(_.streamName)
            .collect { case (_, y) if y.length > 1 => y }
            .isEmpty
        )
        .one
        .sorted

      val streams = streamNames.foldLeft(Streams.empty) {
        case (streams, streamName) =>
          streams.addStream(1, streamName, awsRegion, awsAccountId)._1
      }

      val exclusiveStartStreamName = streamNames(3)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(Some(exclusiveStartStreamName), None)
        res <- req.listStreams(streamsRef)
      } yield assert(
        res.isValid && res.exists { response =>
          streamNames.takeRight(6) == response.streamNames
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })

  test("It should limit properly")(PropF.forAllF {
    (
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streamNames = Gen
        .listOfN(10, Arbitrary.arbitrary[StreamName])
        .suchThat(x =>
          x.groupBy(_.streamName)
            .collect { case (_, y) if y.length > 1 => y }
            .isEmpty
        )
        .one
        .sorted

      val streams = streamNames.foldLeft(Streams.empty) {
        case (streams, streamName) =>
          streams.addStream(1, streamName, awsRegion, awsAccountId)._1
      }

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = ListStreamsRequest(None, Some(5))
        res <- req.listStreams(streamsRef)
      } yield assert(
        res.isValid && res.exists { response =>
          streamNames.take(5) == response.streamNames &&
          response.hasMoreStreams
        },
        s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
      )
  })
}
