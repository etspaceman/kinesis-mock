FROM ghcr.io/etspaceman/graalvm-ce-base:latest as build

ARG DOCKER_SERVICE_JAR
ARG STATIC_TYPE
COPY ${DOCKER_SERVICE_JAR} ./kinesis-mock.jar
COPY graal ./graal

RUN ./graal/buildNativeImageLinux.sh ${STATIC_TYPE}
RUN ./upx-3.96-amd64_linux/upx --best -o kinesis-mock kinesis-mock-native

FROM alpine:3
RUN apk --no-cache add curl
COPY --from=build /app/kinesis-mock /opt/kinesis-mock
EXPOSE 4567 4568
CMD ["/opt/kinesis-mock", "--gc=G1"]
