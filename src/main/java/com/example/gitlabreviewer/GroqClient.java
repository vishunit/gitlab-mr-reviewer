package com.example.gitlabreviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class GroqClient {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String reviewWithGroq(String groqApiUrl, String groqApiKey, String model, String prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            headers.setBearerAuth(groqApiKey);
        }
        HttpEntity<Object> ent = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = rest.postForEntity(groqApiUrl, ent, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API returned " + resp.getStatusCode() + " - " + resp.getBody());
        }
        JsonNode root = mapper.readTree(resp.getBody());
        // try to extract choices -> [0].message.content
        if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
            JsonNode first = root.get("choices").get(0);
            if (first.has("message") && first.get("message").has("content")) {
                return first.get("message").get("content").asText();
            }
            if (first.has("text")) {
                return first.get("text").asText();
            }
        }
        // fallback: return full body
        return resp.getBody();
    }
}
