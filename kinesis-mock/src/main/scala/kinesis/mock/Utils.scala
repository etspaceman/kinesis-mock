package kinesis.mock

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.std.SecureRandom
import cats.effect.std.UUIDGen

object Utils {
  private def getUUIDGen: SyncIO[UUIDGen[SyncIO]] = SecureRandom
    .javaSecuritySecureRandom[SyncIO]
    .map(x => UUIDGen.fromSecureRandom[SyncIO](implicitly, x))

  private def randomUUIDSyncIO: SyncIO[UUID] =
    getUUIDGen.flatMap(x => x.randomUUID)

  def randomUUID: UUID =
    randomUUIDSyncIO.unsafeRunSync()

  def randomUUIDString: String =
    randomUUIDSyncIO.map(_.toString).unsafeRunSync()
  def md5(bytes: Array[Byte]): Array[Byte] = MD5.compute(bytes)
  def now: IO[Instant] =
    IO.realTime.map(d => Instant.EPOCH.plusNanos(d.toNanos))
}
