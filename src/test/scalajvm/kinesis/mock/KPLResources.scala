package kinesis.mock

import software.amazon.kinesis.producer.KinesisProducer

final case class KPLResources(
    functionalTestResources: KinesisFunctionalTestResources,
    kpl: KinesisProducer
)
