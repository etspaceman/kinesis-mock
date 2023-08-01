FROM node:18

ARG DOCKER_SERVICE_FILE
COPY ${DOCKER_SERVICE_FILE} ./main.js
COPY ${DOCKER_SERVICE_FILE}.map ./main.js.map
COPY kinesis-mock/src/main/resources/server.json ./server.json
EXPOSE 4567 4568
CMD ["node", "./main.js"]
