# Slim build of the sibling word-editor project for the CLM stack.
# Skips LibreOffice (only needed for legacy .doc import, which CLM never does —
# CLM seeds documents from generated HTML). Build context is ../word-editor.
FROM node:22-alpine

WORKDIR /app
RUN apk add --no-cache tini

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY server.js ./
COPY server ./server
COPY public ./public
RUN mkdir -p /app/data

ENV HOST=0.0.0.0
ENV PORT=3001
ENV DATA_DIR=/app/data
EXPOSE 3001

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
