package kinesis.mock

import cats.syntax.all._
import enumeratum.scalacheck._
import io.circe.Encoder
import io.circe.Decoder
import io.circe.syntax._
import io.circe.parser._
import scala.reflect.ClassTag

import kinesis.mock.models._
import kinesis.mock.api._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import cats.kernel.Eq

class CirceTests extends munit.ScalaCheckSuite {
  def identityLawTest[A: Encoder: Decoder: Arbitrary: Eq](implicit
      loc: munit.Location,
      CT: ClassTag[A]
  ) =
    property(s"Circe Identity Laws Test for ${CT.runtimeClass.getName()}") {
      forAll { a: A =>
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t${a}\n\tDecoded:\n\t${decoded
          .fold(_.toString, _.toString)}"
      }

    }

  identityLawTest[AwsRegion]
  identityLawTest[Consumer]
  identityLawTest[ConsumerStatus]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[KinesisRecord]
  identityLawTest[ShardLevelMetric]
  identityLawTest[StreamStatus]
  identityLawTest[ScalingType]
  identityLawTest[SequenceNumber]
  identityLawTest[SequenceNumberConstant]
  identityLawTest[SequenceNumberRange]
    

  identityLawTest[ShardFilterType]
  identityLawTest[ShardIteratorType]
  

}
