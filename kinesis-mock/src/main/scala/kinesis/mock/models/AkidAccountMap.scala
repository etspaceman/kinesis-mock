/*
 * Copyright 2021-2026 io.github.etspaceman
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

package kinesis.mock.models

import cats.Eq
import cats.syntax.all.*
import ciris.ConfigDecoder
import ciris.ConfigError
import io.circe.Encoder

final case class AkidAccountMap(value: Map[String, AwsAccountId]):
  def resolve(akid: String, default: AwsAccountId): AwsAccountId =
    value.getOrElse(akid, default)

object AkidAccountMap:
  val empty: AkidAccountMap = AkidAccountMap(Map.empty)

  def parse(raw: String): Either[String, AkidAccountMap] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Right(empty)
    else
      trimmed
        .split(',')
        .toList
        .traverse { entry =>
          entry.split(':').toList match
            case akid :: account :: Nil =>
              val k = akid.trim
              val v = account.trim
              if k.isEmpty || v.isEmpty then
                Left(s"Invalid AKID_TO_ACCOUNT_ID entry: '$entry'")
              else Right(k -> AwsAccountId(v))
            case _ =>
              Left(s"Invalid AKID_TO_ACCOUNT_ID entry: '$entry'")
        }
        .map(pairs => AkidAccountMap(pairs.toMap))

  given ConfigDecoder[String, AkidAccountMap] =
    ConfigDecoder[String].mapEither { (_, s) =>
      parse(s).leftMap(ConfigError(_))
    }

  given Encoder[AkidAccountMap] =
    Encoder[Map[String, AwsAccountId]].contramap(_.value)

  given Eq[AkidAccountMap] = Eq.fromUniversalEquals
