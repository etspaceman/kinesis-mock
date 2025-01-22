package kinesis.mock

import java.time.Instant

import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.std.UUIDGen

object Utils {
  def randomUUID =
    UUIDGen.randomUUID[SyncIO].unsafeRunSync()
  def randomUUIDString =
    UUIDGen.randomString[SyncIO].unsafeRunSync()
  def md5(bytes: Array[Byte]): Array[Byte] = MD5.compute(bytes)
  def now = IO.realTime.map(d => Instant.EPOCH.plusNanos(d.toNanos))
}
