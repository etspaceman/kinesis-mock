FROM ghcr.io/graalvm/graalvm-ce:java11-21.0.0.2 as build
RUN gu install native-image

ARG RESULT_LIB="/staticlibs"

RUN mkdir ${RESULT_LIB} && \
    curl -L -o musl.tar.gz https://musl.libc.org/releases/musl-1.2.1.tar.gz && \
    mkdir musl && tar -xvzf musl.tar.gz -C musl --strip-components 1 && cd musl && \
    ./configure --disable-shared --prefix=${RESULT_LIB} && \
    make && make install && \
    cd / && rm -rf /muscl && rm -f /musl.tar.gz && \
    cp /usr/lib/gcc/x86_64-redhat-linux/8/libstdc++.a ${RESULT_LIB}/lib/

ENV PATH="$PATH:${RESULT_LIB}/bin"
ENV CC="musl-gcc"

RUN curl -L -o zlib.tar.gz https://zlib.net/zlib-1.2.11.tar.gz && \
   mkdir zlib && tar -xvzf zlib.tar.gz -C zlib --strip-components 1 && cd zlib && \
   ./configure --static --prefix=${RESULT_LIB} && \
    make && make install && \
    cd / && rm -rf /zlib && rm -f /zlib.tar.gz

ARG DOCKER_SERVICE_JAR
COPY ${DOCKER_SERVICE_JAR} ./kinesis-mock.jar
COPY graal ./graal

RUN native-image \
    --no-server \
    --static \
    --libc=musl \
    -J-Xmx7G \
    --no-fallback \
    --verbose \
    --enable-all-security-services \
    --enable-url-protocols=http,https \
    --initialize-at-build-time=scala \
    -H:ReflectionConfigurationFiles=graal/reflect-config.json \
    -H:ResourceConfigurationFiles=graal/resource-config.json \
    -H:+ReportExceptionStackTraces \
    -H:+AddAllCharsets \
    --report-unsupported-elements-at-runtime \
    --allow-incomplete-classpath \
    --install-exit-handlers \
    -jar kinesis-mock.jar \
    kinesis-mock-native

RUN microdnf install xz && \
    curl -sL -o - https://github.com/upx/upx/releases/download/v3.96/upx-3.96-amd64_linux.tar.xz | tar xJ && \
    ./upx-3.96-amd64_linux/upx --best -o kinesis-mock kinesis-mock-native

FROM alpine:3
RUN apk --no-cache add curl
COPY --from=build kinesis-mock /opt/kinesis-mock
EXPOSE 4567 4568
CMD ["/opt/kinesis-mock", "--gc=G1"]
