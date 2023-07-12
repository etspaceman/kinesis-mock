FROM node:18

ARG DOCKER_SERVICE_FILE
ENV KINESIS_MOCK_CERT_PATH="server.json"
COPY ${DOCKER_SERVICE_FILE} ./main.js
COPY ${DOCKER_SERVICE_FILE}.map ./main.js.map
COPY kinesis-mock/src/main/resources/server.json ./server.json
EXPOSE 4567 4568
CMD ["node", "--enable-source-maps", "./main.js"]
