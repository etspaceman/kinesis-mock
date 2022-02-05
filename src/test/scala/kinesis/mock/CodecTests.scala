package kinesis.mock

import scala.reflect.ClassTag

import cats.Eq
import cats.syntax.all._
import io.bullet.borer
import io.bullet.borer.Cbor
import io.circe
import io.circe.parser._
import io.circe.syntax._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._

trait CodecTests extends munit.ScalaCheckSuite {
  def identityLawTest[A: Encoder: Decoder: Arbitrary: Eq](implicit
      loc: munit.Location,
      CT: ClassTag[A]
  ): Unit = {
    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Circe"
    ) {
      forAll { a: A =>
        implicit val E: circe.Encoder[A] = Encoder[A].circeEncoder
        implicit val D: circe.Decoder[A] = Decoder[A].circeDecoder
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }

    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Circe CBOR"
    ) {
      forAll { a: A =>
        implicit val E: circe.Encoder[A] = Encoder[A].circeCborEncoder
        implicit val D: circe.Decoder[A] = Decoder[A].circeCborDecoder
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }

    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Borer"
    ) {
      forAll { a: A =>
        implicit val E: borer.Encoder[A] = Encoder[A].borerEncoder
        implicit val D: borer.Decoder[A] = Decoder[A].borerDecoder
        val encoded = Cbor.encode(a).toByteArray
        val decoded = Cbor.decode(encoded).to[A].valueEither

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }
  }

}
