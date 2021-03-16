package kinesis.mock

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

class CirceTests extends munit.ScalaCheckSuite {
  def identityLawTest[A: Encoder: Decoder: Arbitrary](implicit
      loc: munit.Location,
      CT: ClassTag[A]
  ) =
    property(s"Circe Identity Laws Test for ${CT.runtimeClass.getName()}") {
      forAll { a: A =>
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.contains(
          a
        ) :| s"\n\tInput: ${a}\n\tEncoded: ${encoded}\n\tDecoded: ${decoded}"
      }

    }

  identityLawTest[AwsRegion]
  identityLawTest[Consumer]
  identityLawTest[ConsumerStatus]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[ShardLevelMetric]
  identityLawTest[StreamStatus]
  identityLawTest[ScalingType]
  identityLawTest[ShardFilterType]
  identityLawTest[ShardIteratorType]

}
