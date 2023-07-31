package kinesis.mock

import cats.effect.IO
import cats.effect.std.Queue
import software.amazon.kinesis.retrieval.KinesisClientRecord

final case class KCLResources(
    functionalTestResources: KinesisFunctionalTestResources,
    resultsQueue: Queue[IO, KinesisClientRecord]
)
