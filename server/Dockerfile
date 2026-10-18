FROM node:22-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
COPY public ./public
ENV NODE_ENV=production \
    PORT=8080 \
    DATA_FILE=/data/dati.json
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "src/main.js"]
