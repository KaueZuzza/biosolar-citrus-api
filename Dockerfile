# BioSolar Citrus: imagem para hospedagem (Render, Railway, qualquer servico com Docker).
# O dashboard (frontend/) vai dentro do .jar; o banco e informado por variaveis de ambiente (ver render.yaml).
# Os testes rodam no desenvolvimento (mvnw test); aqui so o empacotamento.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY frontend frontend
RUN mvn -q -B -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 biosolar
COPY --from=build /src/backend/target/*.jar /app/app.jar
USER biosolar
# Planos gratuitos tem ~512 MB: limita a memoria da JVM e acelera a inicializacao
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
