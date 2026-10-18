FROM node:22-alpine
LABEL org.opencontainers.image.source="https://github.com/16364-Cedric/test" \
      org.opencontainers.image.description="Serveur de supervision DATI"
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
