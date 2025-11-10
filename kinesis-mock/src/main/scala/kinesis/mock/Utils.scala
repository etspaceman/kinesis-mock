package kinesis.mock

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.effect.std.SecureRandom
import cats.effect.std.UUIDGen

object Utils:
  private def getUUIDGen: IO[UUIDGen[IO]] = SecureRandom
    .javaSecuritySecureRandom[IO]
    .map(x => UUIDGen.fromSecureRandom[IO](implicitly, x))

  private def randomUUIDIO: IO[UUID] = getUUIDGen.flatMap(x => x.randomUUID)

  def randomUUID: IO[UUID] = getUUIDGen.flatMap(x => x.randomUUID)

  def randomUUIDString: IO[String] = randomUUIDIO.map(_.toString)
  def md5(bytes: Array[Byte]): Array[Byte] = MD5.compute(bytes)
  def now: IO[Instant] =
    IO.realTime.map(d => Instant.EPOCH.plusNanos(d.toNanos))
