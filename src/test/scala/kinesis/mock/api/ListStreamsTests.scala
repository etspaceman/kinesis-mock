package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamsTests extends munit.ScalaCheckSuite {
  property("It should list streams")(forAll {
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

      val req = ListStreamsRequest(None, None)
      val res = req.listStreams(streams)

      (res.isValid && res.exists { response =>
        streamNames == response.streamNames
      }) :| s"diff: ${res.map(r => r.streamNames.diff(r.streamNames))}"
  })

  property("It should filter by exclusiveStartStreamName")(forAll {
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

      val req = ListStreamsRequest(Some(exclusiveStartStreamName), None)
      val res = req.listStreams(streams)

      (res.isValid && res.exists { response =>
        streamNames.takeRight(6) == response.streamNames
      }) :| s"diff: ${res.map(r => streamNames.diff(r.streamNames))}"
  })

  property("It should limit properly")(forAll {
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

      val req = ListStreamsRequest(None, Some(5))
      val res = req.listStreams(streams)

      (res.isValid && res.exists { response =>
        streamNames.take(5) == response.streamNames &&
        response.hasMoreStreams
      }) :| s"diff: ${res.map(r => streamNames.diff(r.streamNames))}"
  })
}
