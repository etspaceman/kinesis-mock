package kinesis.mock
package api

import scala.collection.SortedMap

import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class RegisterStreamConsumerTests extends munit.ScalaCheckSuite {
  property("It should register stream consumers")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val streamArn = streams.streams(streamName).streamArn

      val req =
        RegisterStreamConsumerRequest(consumerName, streamArn)
      val res =
        req.registerStreamConsumer(streams)

      (res.isValid && res.exists { case (s, _) =>
        s.streams.get(streamName).exists { stream =>
          stream.consumers
            .get(consumerName)
            .nonEmpty
        }
      }) :| s"req: $req\nres: $res"
  })

  property("It should reject when there are 20 consumers")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(20, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .one
          .map(c => c.consumerName -> c)
      )

      val updated = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = updated.streams(streamName).streamArn

      val req =
        RegisterStreamConsumerRequest(consumerName, streamArn)
      val res =
        req.registerStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })

  property("It should reject when there are 5 consumers being created")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        consumerName: ConsumerName
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val consumers = SortedMap.from(
        Gen
          .listOfN(5, consumerArbitrary.arbitrary)
          .suchThat(x =>
            x.groupBy(_.consumerName)
              .collect { case (_, y) if y.length > 1 => x }
              .isEmpty
          )
          .map(_.map(c => c.copy(consumerStatus = ConsumerStatus.CREATING)))
          .one
          .map(c => c.consumerName -> c)
      )

      val updated = streams.findAndUpdateStream(streamName)(s =>
        s.copy(consumers = consumers)
      )

      val streamArn = updated.streams(streamName).streamArn

      val req =
        RegisterStreamConsumerRequest(consumerName, streamArn)
      val res =
        req.registerStreamConsumer(updated)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
