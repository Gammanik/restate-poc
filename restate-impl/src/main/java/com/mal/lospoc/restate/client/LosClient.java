package com.mal.lospoc.restate.client;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.IOException;
import java.util.UUID;

public class LosClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public LosClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }

    public void applyEvent(UUID applicationId, ApplicationEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                .url(baseUrl + "/internal/applications/" + applicationId + "/events")
                .post(body)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to apply event: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply event", e);
        }
    }
}
