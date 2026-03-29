package com.qualitygate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LLMService {  // ollama 0.17.4

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "mistral";

    private final HttpClient client;
    private final ObjectMapper mapper;

    public LLMService() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public String generateTests(String prompt) throws Exception {

        // Build request JSON
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", MODEL_NAME);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API error: " + response.body());
        }

        var json = mapper.readTree(response.body());

        if (!json.has("response")) {
            throw new RuntimeException("Unexpected Ollama response: " + response.body());
        }

        return json.get("response").asText();
    }
    public String generateTestsWithTrace(String prompt, String className, String repoPath, String runId) throws Exception {

        // 1. Generate normally
        String response = generateTests(prompt);

        // 2. Create trace directory
        File traceDir = new File(repoPath + "/.qualitygate/traces/"+ runId + "/" + className);
        traceDir.mkdirs();

        // 3. Save prompt
        Files.writeString(Path.of(traceDir.getAbsolutePath(), "prompt.txt"), prompt);

        // 4. Save response
        Files.writeString(Path.of(traceDir.getAbsolutePath(), "response.txt"), response);

        // 5. Save metadata
        String metadata = "{\n" +
                "  \"class\": \"" + className + "\",\n" +
                "  \"model\": \"" + MODEL_NAME + "\",\n" +
                "  \"runId\": \"" + runId + "\",\n" +
                "  \"promptHash\": \"" + Integer.toHexString(prompt.hashCode()) + "\"\n" +
                "}";

        Files.writeString(Path.of(traceDir.getAbsolutePath(), "metadata.json"), metadata);

        return response;
    }
}