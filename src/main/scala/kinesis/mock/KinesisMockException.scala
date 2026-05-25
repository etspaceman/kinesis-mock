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

sealed trait KinesisMockException extends Exception:
  def msg: String
  override def getMessage: String = msg

final case class InvalidArgumentException(msg: String)
    extends KinesisMockException
final case class ExpiredIteratorException(msg: String)
    extends KinesisMockException
final case class LimitExceededException(msg: String)
    extends KinesisMockException
final case class ResourceInUseException(msg: String)
    extends KinesisMockException
final case class ResourceNotFoundException(msg: String)
    extends KinesisMockException
