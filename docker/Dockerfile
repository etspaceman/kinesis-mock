FROM ghcr.io/graalvm/graalvm-ce:ol8-java11-22.2.0 as build
RUN gu install native-image

RUN mkdir /musl && \
    curl -L -o musl.tar.gz https://more.musl.cc/10.2.1/x86_64-linux-musl/x86_64-linux-musl-native.tgz && \
    tar -xvzf musl.tar.gz -C /musl --strip-components 1 && \
    cp /usr/lib/gcc/x86_64-redhat-linux/8/libstdc++.a /musl/lib/

ENV CC=/musl/bin/gcc

RUN curl -L -o zlib.tar.gz https://zlib.net/zlib-1.2.12.tar.gz && \
    mkdir zlib && tar -xvzf zlib.tar.gz -C zlib --strip-components 1 && \
    cd zlib && ./configure --static --prefix=/musl && \
    make && make install && \
    cd / && rm -rf /zlib && rm -f /zlib.tar.gz

ENV PATH="$PATH:/musl/bin"

ARG DOCKER_SERVICE_JAR
ARG STATIC_TYPE
COPY ${DOCKER_SERVICE_JAR} ./kinesis-mock.jar
COPY graal ./graal

RUN ./graal/buildNativeImageLinux.sh ${STATIC_TYPE}

RUN microdnf install xz && \
    curl -sL -o - https://github.com/upx/upx/releases/download/v3.96/upx-3.96-amd64_linux.tar.xz | tar xJ && \
    ./upx-3.96-amd64_linux/upx --best -o kinesis-mock kinesis-mock-native

FROM alpine:3
RUN apk --no-cache add curl
COPY --from=build kinesis-mock /opt/kinesis-mock
EXPOSE 4567 4568
CMD ["/opt/kinesis-mock", "--gc=G1"]
