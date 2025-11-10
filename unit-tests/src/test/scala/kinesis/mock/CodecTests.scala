/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock

import scala.reflect.ClassTag

import cats.Eq
import cats.syntax.all.*
import io.bullet.borer
import io.bullet.borer.Cbor
import io.circe
import io.circe.parser.*
import io.circe.syntax.*
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.*

trait CodecTests extends munit.ScalaCheckSuite:
  def identityLawTest[A: Encoder: Decoder: Arbitrary: Eq](using
      loc: munit.Location,
      CT: ClassTag[A]
  ): Unit =
    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Circe"
    ) {
      forAll { (a: A) =>
        given E: circe.Encoder[A] = Encoder[A].circeEncoder
        given D: circe.Decoder[A] = Decoder[A].circeDecoder
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }

    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Circe CBOR"
    ) {
      forAll { (a: A) =>
        given E: circe.Encoder[A] = Encoder[A].circeCborEncoder
        given D: circe.Decoder[A] = Decoder[A].circeCborDecoder
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }

    property(
      s"Codec Identity Laws Test for ${CT.runtimeClass.getName} - Borer"
    ) {
      forAll { (a: A) =>
        given E: borer.Encoder[A] = Encoder[A].borerEncoder
        given D: borer.Decoder[A] = Decoder[A].borerDecoder
        val encoded = Cbor.encode(a).toByteArray
        val decoded = Cbor.decode(encoded).to[A].valueEither

        decoded.exists(_ === a) :| s"\n\tInput:\n\t$a\n\tDecoded:\n\t${decoded
            .fold(_.toString, _.toString)}"
      }
    }
