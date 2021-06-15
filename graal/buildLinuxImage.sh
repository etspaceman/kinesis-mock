VERSION=$(cat version.sbt | cut -d '"' -f2)

mkdir shared && \
docker run --rm -v ${PWD}/shared:/shared ghcr.io/etspaceman/kinesis-mock:${VERSION} cp /opt/kinesis-mock /shared/kinesis-mock