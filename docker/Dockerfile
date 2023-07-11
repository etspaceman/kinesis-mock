FROM node:20

ARG DOCKER_SERVICE_FILE
ENV KINESIS_MOCK_CERT_PATH="server.json"
COPY ${DOCKER_SERVICE_FILE} ./kinesis-mock.js
COPY ${DOCKER_SERVICE_FILE}.map ./kinesis-mock.js.map
COPY kinesis-mock/src/main/resources/server.json ./server.json
EXPOSE 4567 4568
CMD ["node", "--enable-source-maps", "./kinesis-mock.js"]
