STATIC_TYPE=${STATIC_TYPE:-static}

if [[ $STATIC_TYPE = "static" ]]; then
    STATIC_ARG="--static --libc=musl"
elif [[ $STATIC_TYPE = "mostly-static" ]]; then
    STATIC_ARG="-H:+StaticExecutableWithDynamicLibC"
else
    STATIC_ARG=""
fi

native-image \
    --no-server \
    ${STATIC_ARG} \
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