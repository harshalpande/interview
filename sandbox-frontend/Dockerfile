FROM eclipse-temurin:17-jdk-jammy as java-runtime

FROM node:20.19.0-bookworm-slim
WORKDIR /app
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /opt/angular-template
COPY sandbox-frontend/template/angular-workspace/package.json ./package.json
COPY sandbox-frontend/template/angular-workspace/angular.json ./angular.json
COPY sandbox-frontend/template/angular-workspace/tsconfig.json ./tsconfig.json
COPY sandbox-frontend/template/angular-workspace/tsconfig.app.json ./tsconfig.app.json
COPY sandbox-frontend/template/angular-workspace/src ./src
RUN npm install --no-audit --no-fund

WORKDIR /opt/react-template
COPY sandbox-frontend/template/react-workspace/package.json ./package.json
COPY sandbox-frontend/template/react-workspace/tsconfig.json ./tsconfig.json
COPY sandbox-frontend/template/react-workspace/tsconfig.node.json ./tsconfig.node.json
COPY sandbox-frontend/template/react-workspace/vite.config.ts ./vite.config.ts
COPY sandbox-frontend/template/react-workspace/index.html ./index.html
COPY sandbox-frontend/template/react-workspace/src ./src
RUN npm install --no-audit --no-fund

WORKDIR /app

COPY sandbox-frontend/target/sandbox-frontend-0.0.1-SNAPSHOT.jar app.jar

ENV ANGULAR_TEMPLATE_ROOT=/opt/angular-template
ENV REACT_TEMPLATE_ROOT=/opt/react-template

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
