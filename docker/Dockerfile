FROM ghcr.io/graalvm/graalvm-ce:ol8-java11-22.2.0 as build
RUN gu install native-image

ARG RESULT_LIB="/staticlibs"

RUN mkdir ${RESULT_LIB} && \
    curl -L -o musl.tar.gz https://musl.libc.org/releases/musl-1.2.3.tar.gz && \
    mkdir musl && tar -xvzf musl.tar.gz -C musl --strip-components 1 && cd musl && \
    ./configure --disable-shared --prefix=${RESULT_LIB} && \
    make && make install && \
    cd / && rm -rf /muscl && rm -f /musl.tar.gz && \
    cp /usr/lib/gcc/x86_64-redhat-linux/8/libstdc++.a ${RESULT_LIB}/lib/

ENV PATH="$PATH:${RESULT_LIB}/bin"
ENV CC="musl-gcc"

RUN curl -L -o zlib.tar.gz https://zlib.net/zlib-1.2.12.tar.gz && \
   mkdir zlib && tar -xvzf zlib.tar.gz -C zlib --strip-components 1 && cd zlib && \
   ./configure --static --prefix=${RESULT_LIB} && \
    make && make install && \
    cd / && rm -rf /zlib && rm -f /zlib.tar.gz

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
