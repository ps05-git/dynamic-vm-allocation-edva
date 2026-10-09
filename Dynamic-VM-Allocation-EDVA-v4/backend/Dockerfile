FROM eclipse-temurin:21-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends nodejs npm && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY src ./src
COPY backend ./backend
WORKDIR /app/backend
ENV PORT=3000
EXPOSE 3000
CMD ["node","server.js"]
