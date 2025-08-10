package com.example.gitlabreviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@Service
public class GitlabService {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public int fetchProjectId(String gitlabHost, String projectIdOrPath, String privateToken) throws Exception {
        // Accept either numeric project ID or path
        projectIdOrPath = projectIdOrPath == null ? "" : projectIdOrPath.trim();
        privateToken = privateToken == null ? "" : privateToken.trim();
        String url;
        if (projectIdOrPath.matches("\\d+")) {
            // Numeric project ID, use directly
            url = gitlabHost + "/api/v4/projects/" + projectIdOrPath;
        } else {
            // Path, encode as before
            String encoded = projectIdOrPath.contains("%2F") ? projectIdOrPath : URLEncoder.encode(projectIdOrPath, StandardCharsets.UTF_8);
            url = gitlabHost + "/api/v4/projects/" + encoded;
        }
        String finalUrl = UriComponentsBuilder.fromHttpUrl(url).build(false).toUriString();
        System.out.println("projectIdOrPath: " + projectIdOrPath);
        // Update log message for clarity
        if (projectIdOrPath.matches("\\d+")) {
            System.out.println("Using numeric project ID: " + projectIdOrPath);
        } else {
            System.out.println("ERROR: Only numeric project ID is supported. Please set -Dgitlab.repoPath to the project ID, not the path.");
            throw new IllegalArgumentException("Only numeric project ID is supported. Set -Dgitlab.repoPath to the project ID.");
        }
        System.out.println("Final URL: " + finalUrl);
        System.out.println("Using token: " + (privateToken != null ? "[hidden]" : "null"));
        HttpHeaders headers = new HttpHeaders();
        if (!privateToken.isBlank()) {
            headers.set("PRIVATE-TOKEN", privateToken);
        }
        System.out.println("Token: " + privateToken);
        System.out.println("headers: " + headers);
        HttpEntity<Void> ent = new HttpEntity<>(headers);
        ResponseEntity<String> resp = null;
        try {
            resp = rest.exchange(finalUrl, HttpMethod.GET, ent, String.class);
            System.out.println("resp: " + resp);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("HTTP error when fetching project: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch project: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        }
        if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to fetch project: " + (resp != null ? resp.getStatusCode() : "null") + " - " + (resp != null ? resp.getBody() : "null"));
        }
        String body = resp.getBody();
        // Check if response is JSON
        if (body == null || !body.trim().startsWith("{") || body.trim().startsWith("<")) {
            throw new RuntimeException("Unexpected response format (not JSON): " + body);
        }
        JsonNode node = mapper.readTree(body);
        if (node.has("message")) {
            throw new RuntimeException("GitLab API error: " + node.get("message").asText());
        }
        return node.get("id").asInt();
    }


    public String fetchMrDiff(String gitlabHost, int projectId, String mrIid, String privateToken) throws Exception {
        String url = gitlabHost + "/api/v4/projects/" + projectId + "/merge_requests/" + mrIid + "/changes";
        HttpHeaders headers = new HttpHeaders();
        if (privateToken != null && !privateToken.isBlank()) {
            headers.set("PRIVATE-TOKEN", privateToken);
        }
        HttpEntity<Void> ent = new HttpEntity<>(headers);
        ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, ent, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to fetch MR changes: " + resp.getStatusCode() + " - " + resp.getBody());
        }
        JsonNode root = mapper.readTree(resp.getBody());
        JsonNode changes = root.get("changes");
        if (changes == null || !changes.isArray()) {
            // sometimes GitLab returns the MR directly (when calling /merge_requests/:iid) - try fallback
            if (root.has("diffs")) {
                changes = root.get("diffs");
            }
        }
        StringBuilder sb = new StringBuilder();
        if (changes != null && changes.isArray()) {
            Iterator<JsonNode> it = changes.elements();
            while (it.hasNext()) {
                JsonNode c = it.next();
                String newPath = c.has("new_path") ? c.get("new_path").asText() : c.path("file_path").asText();
                sb.append("---- File: ").append(newPath).append("\n");
                if (c.has("diff")) {
                    sb.append(c.get("diff").asText()).append("\n\n");
                } else if (c.has("diffs")) {
                    sb.append(c.get("diffs").toString()).append("\n\n");
                } else {
                    // try to combine old+new content if available
                    if (c.has("old_path") || c.has("new_path")) {
                        sb.append("(no textual diff available)\n\n");
                    }
                }
            }
        } else {
            sb.append("(no diffs found in MR response)\n");
        }
        return sb.toString();
    }
}
