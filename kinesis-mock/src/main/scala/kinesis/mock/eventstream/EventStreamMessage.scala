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

package kinesis.mock.eventstream

import scodec.bits.ByteVector

/** An AWS event-stream message. Wire layout: prelude (12) | headers (var) |
  * payload (var) | message_crc (4)
  *
  * Prelude = total_len (u32 BE) | headers_len (u32 BE) | prelude_crc (u32 BE)
  */
final case class EventStreamMessage(
    headers: List[EventStreamHeader],
    payload: ByteVector
)

object EventStreamMessage:
  def encode(msg: EventStreamMessage): ByteVector =
    val headerBytes = EventStreamHeader.encodeAll(msg.headers)
    val totalLen = 12 + headerBytes.length + msg.payload.length + 4
    val preludeFront =
      ByteVector.fromInt(totalLen.toInt) ++
        ByteVector.fromInt(headerBytes.length.toInt)
    val preludeCrc = ByteVector.fromInt(Crc32.compute(preludeFront).toInt)
    val withoutTrailingCrc =
      preludeFront ++ preludeCrc ++ headerBytes ++ msg.payload
    val messageCrc = ByteVector.fromInt(Crc32.compute(withoutTrailingCrc).toInt)
    withoutTrailingCrc ++ messageCrc

  /** Build an `event` frame for a typed SubscribeToShard event. */
  def event(
      eventType: String,
      contentType: String,
      payload: ByteVector
  ): EventStreamMessage =
    EventStreamMessage(
      headers = List(
        EventStreamHeader(":message-type", "event"),
        EventStreamHeader(":event-type", eventType),
        EventStreamHeader(":content-type", contentType)
      ),
      payload = payload
    )

  /** Build an `exception` frame, used to surface errors mid-stream (e.g.
    * ResourceInUse).
    */
  def exception(
      exceptionType: String,
      contentType: String,
      payload: ByteVector
  ): EventStreamMessage =
    EventStreamMessage(
      headers = List(
        EventStreamHeader(":message-type", "exception"),
        EventStreamHeader(":exception-type", exceptionType),
        EventStreamHeader(":content-type", contentType)
      ),
      payload = payload
    )
