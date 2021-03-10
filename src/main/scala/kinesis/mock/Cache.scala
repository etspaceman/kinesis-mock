package kinesis.mock

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, IO}
import cats.syntax.all._

import kinesis.mock.api._
import kinesis.mock.models._
import scala.concurrent.duration._
import ciris._
import cats.effect.Timer
import cats.effect.ContextShift

class Cache private (
    ref: Ref[IO, Streams],
    semaphores: CacheSemaphores,
    config: CacheConfig
) {

  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req.addTagsToStream(streams).traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for AddTagsToStreamRequest"
          )
        )
      )
    )

  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req
          .removeTagsFromStream(streams)
          .traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RemoveTagsFromStreamRequest"
          )
        )
      )
    )

  def createStream(
      req: CreateStreamRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] = for {
    streams <- ref.get
    res <- req
      .createStream(
        streams,
        config.shardLimit,
        config.awsRegion,
        config.awsAccountId
      )
      .traverse(updated =>
        (IO.sleep(config.createStreamDuration) *>
          IO(
            updated.findAndUpdateStream(req.streamName)(x =>
              x.copy(status = StreamStatus.ACTIVE)
            )
          )).start.void
      )
  } yield res
}

object Cache {
  def apply(config: CacheConfig)(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    semaphores <- CacheSemaphores.create
  } yield new Cache(ref, semaphores, config)
}

final case class CacheSemaphores private (
    addTagsToStream: Semaphore[IO],
    removeTagsFromStream: Semaphore[IO]
)

object CacheSemaphores {
  def create(implicit C: Concurrent[IO]): IO[CacheSemaphores] = for {
    addTagsToStream <- Semaphore[IO](5)
    removeTagsFromStream <- Semaphore[IO](5)
  } yield new CacheSemaphores(addTagsToStream, removeTagsFromStream)
}

final case class CacheConfig(
    createStreamDuration: FiniteDuration,
    shardLimit: Int,
    awsAccountId: String,
    awsRegion: AwsRegion
)

object CacheConfig {
  def read: ConfigValue[CacheConfig] = for {
    createStreamDuration <- env("CREATE_STREAM_DURATION")
      .as[FiniteDuration]
      .default(500.millis)
    shardLimit <- env("SHARD_LIMIT").as[Int].default(50)
    awsAccountId <- env("AWS_ACCOUNT_ID").as[String].default("000000000000")
    awsRegion <- env("AWS_REGION")
      .or(env("AWS_DEFAULT_REGION"))
      .as[AwsRegion]
      .default(AwsRegion.US_EAST_1)
  } yield CacheConfig(createStreamDuration, shardLimit, awsAccountId, awsRegion)
}
