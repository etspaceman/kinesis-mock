FROM node:25-alpine3.21

ARG DOCKER_SERVICE_FILE
COPY ${DOCKER_SERVICE_FILE} ./main.js
COPY ${DOCKER_SERVICE_FILE}.map ./main.js.map
COPY kinesis-mock/src/main/resources/server.json ./server.json
EXPOSE 4567 4568
RUN apk --no-cache add curl
CMD ["node", "./main.js"]
