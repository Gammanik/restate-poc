package com.mal.lospoc.restate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

public class HttpbinClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public HttpbinClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    protected <T> T post(String path, Object requestBody, Class<T> responseType) {
        try {
            String json = mapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(body)
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP error: " + response.code() + " " + response.message());
                }
                String responseJson = response.body().string();
                return mapper.readValue(responseJson, responseType);
            }
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> postForMap(String path, Object requestBody) {
        return post(path, requestBody, Map.class);
    }
}
