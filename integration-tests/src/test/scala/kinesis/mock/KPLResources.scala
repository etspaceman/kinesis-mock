package kinesis.mock

import com.amazonaws.services.kinesis.producer.KinesisProducer

final case class KPLResources(
    functionalTestResources: KinesisFunctionalTestResources,
    kpl: KinesisProducer
)
