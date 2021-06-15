RESULT_LIB="staticlibs"

sudo apt-get install -y apt-file && apt-file update *&& apt-file find libstdc++.a

mkdir ${RESULT_LIB} && \
    curl -L -o musl.tar.gz https://musl.libc.org/releases/musl-1.2.1.tar.gz && \
    mkdir musl && tar -xvzf musl.tar.gz -C musl --strip-components 1 && cd musl && \
    ./configure --disable-shared --prefix=${RESULT_LIB} && \
    make && make install && \
    cd / && rm -rf /muscl && rm -f /musl.tar.gz && \
    cp /usr/lib/debug/libstdc++.a ${RESULT_LIB}/lib/

echo "${RESULT_LIB}/bin" >> $GITHUB_PATH
echo "CC=musl-gcc" >> $GITHUB_ENV

curl -L -o zlib.tar.gz https://zlib.net/zlib-1.2.11.tar.gz && \
   mkdir zlib && tar -xvzf zlib.tar.gz -C zlib --strip-components 1 && cd zlib && \
   ./configure --static --prefix=${RESULT_LIB} && \
   make && make install && \
   cd / && rm -rf /zlib && rm -f /zlib.tar.gz