FROM openjdk:17-jdk-alpine
COPY . /app
WORKDIR /app
RUN javac Servidor.java
CMD ["java", "Servidor"]
