FROM node:20 as build

ARG DOCKER_SERVICE_FILE
ENV KINESIS_MOCK_CERT_PATH="server.json"
COPY ${DOCKER_SERVICE_FILE} ./kinesis-mock.js
COPY kinesis-mock/src/main/resources/server.json ./server.json
EXPOSE 4567 4568
CMD ["node", "./kinesis-mock.js"]
