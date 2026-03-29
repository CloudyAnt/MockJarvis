# Mock Jarvis

For now, it just a simple & fun project to explore about Agents which build on top of Spring AI Alibaba.
For long term, maybe it can turn into an AI butler.

## Run The Spring Boot API

```bash
gradle bootRun
```

The server listens on `http://localhost:8080` by default.

## Run The JavaFX Client

```bash
gradle :javafx-client:run
```

The JavaFX client calls `http://localhost:8080/api/v1/query_weather` by default.

You can change the service address directly in the client UI before sending a request.

If your server runs on another address, override it with:

```bash
MOCK_JARVIS_API_BASE_URL=http://localhost:8081 gradle :javafx-client:run
```

That environment variable only sets the initial value in the UI. You can still edit it after the client starts.