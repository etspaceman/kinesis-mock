package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests extends munit.ScalaCheckSuite {
  property("It should list consumers")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(5, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val withConsumers = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = withConsumers.streams(streamName).streamArn

      val req =
        ListStreamConsumersRequest(None, None, streamArn, None)
      val res = req.listStreamConsumers(withConsumers)

      (res.isValid && res.exists { response =>
        consumers.values.toList == response.consumers
      }) :| s"req: $req\nres: $res"
  })

  property("It should paginate properly")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(100, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(10, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val withConsumers = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = withConsumers.streams(streamName).streamArn

      val req =
        ListStreamConsumersRequest(Some(5), None, streamArn, None)

      val res = req.listStreamConsumers(withConsumers)
      val paginatedRes = res.andThen { result =>
        val paginatedReq =
          ListStreamConsumersRequest(
            Some(5),
            result.nextToken,
            streamArn,
            None
          )
        paginatedReq.listStreamConsumers(withConsumers)
      }

      (res.isValid && paginatedRes.isValid && res.exists { response =>
        consumers.values.take(5) == response.consumers
      } && paginatedRes.exists { response =>
        consumers.values.takeRight(5) == response.consumers
      }) :| s"req: $req\n" +
        s"resCount: ${res.map(_.consumers.length)}\n" +
        s"paginatedResCount: ${paginatedRes.map(_.consumers.length)}}"
  })

}
