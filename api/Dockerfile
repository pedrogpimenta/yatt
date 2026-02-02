FROM node:20-alpine

# Install build dependencies for better-sqlite3
RUN apk add --no-cache python3 make g++

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install --production

# Copy source code
COPY src ./src

# Create data directory for SQLite
RUN mkdir -p /app/data

ENV PORT=3000
ENV DB_PATH=/app/data/yatt.db

EXPOSE 3000

CMD ["npm", "start"]
