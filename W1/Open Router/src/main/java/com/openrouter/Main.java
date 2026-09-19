package com.openrouter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class Main {
    private static final String AUTH_KEY_ENDPOINT = "https://openrouter.ai/api/v1/auth/key";
    private static final String CREDITS_ENDPOINT = "https://openrouter.ai/api/v1/credits";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    public static void main(String[] args) {
        boolean jsonOutput = false;
        String testError = null;

        for (String arg : args) {
            if ("--json".equals(arg)) {
                jsonOutput = true;
            } else if (arg.startsWith("--test-error=")) {
                testError = arg.substring("--test-error=".length());
            }
        }

        String apiKey = System.getenv("OPENROUTER_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            apiKey = loadFromEnvFile();
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: OPENROUTER_API_KEY not found in environment or .env file");
            System.exit(1);
        }

        if (testError != null) {
            try {
                simulateError(testError);
            } catch (ApiException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
            return;
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build()) {

            JsonObject keyInfo = fetchAuthKey(client, apiKey);
            JsonObject creditsInfo = fetchCreditsInfo(client, apiKey);

            JsonObject merged = new JsonObject();
            if (keyInfo != null) {
                for (String key : keyInfo.keySet()) {
                    merged.add(key, keyInfo.get(key));
                }
            }
            if (creditsInfo != null) {
                for (String key : creditsInfo.keySet()) {
                    merged.add(key, creditsInfo.get(key));
                }
            }

            if (jsonOutput) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                System.out.println(gson.toJson(merged));
            } else {
                printReport(keyInfo, creditsInfo);
            }

            System.exit(0);
        } catch (ApiException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static JsonObject fetchAuthKey(HttpClient client, String apiKey) throws ApiException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUTH_KEY_ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return handleResponse(response, "auth/key");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted: " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new ApiException("Network error: " + e.getMessage());
        }
    }

    private static JsonObject fetchCreditsInfo(HttpClient client, String apiKey) throws ApiException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CREDITS_ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403) {
                return null;
            }

            return handleResponse(response, "credits");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted: " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new ApiException("Network error: " + e.getMessage());
        }
    }

    private static JsonObject handleResponse(HttpResponse<String> response, String endpoint) throws ApiException {
        switch (response.statusCode()) {
            case 200 -> {
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                if (root.has("data")) {
                    return root.getAsJsonObject("data");
                }
                return root;
            }
            case 401 -> throw new ApiException("Authentication failed: Invalid or expired API key");
            case 403 -> throw new ApiException("Access denied: Insufficient permissions for this operation");
            case 429 -> throw new ApiException("Rate limited: Too many requests, please retry later");
            default -> {
                if (response.statusCode() >= 500) {
                    throw new ApiException("Server error: OpenRouter API is experiencing issues (HTTP " + response.statusCode() + ")");
                }
                throw new ApiException("HTTP error: " + response.statusCode());
            }
        }
    }

    private static void printReport(JsonObject keyInfo, JsonObject creditsInfo) {
        System.out.println("Key Identity");
        if (keyInfo != null && keyInfo.has("label") && !keyInfo.get("label").isJsonNull()) {
            System.out.println("  " + keyInfo.get("label").getAsString());
        }

        System.out.println("\nLimits and Balance");
        if (keyInfo != null) {
            if (keyInfo.has("limit") && !keyInfo.get("limit").isJsonNull()) {
                System.out.println("  Rate Limit: " + keyInfo.get("limit") + "/min");
            }
            if (keyInfo.has("limit_remaining") && !keyInfo.get("limit_remaining").isJsonNull()) {
                System.out.println("  Limit Remaining: $" + String.format("%.4f", keyInfo.get("limit_remaining").getAsDouble()));
            }
            if (keyInfo.has("limit_reset") && !keyInfo.get("limit_reset").isJsonNull()) {
                System.out.println("  Limit Reset: " + keyInfo.get("limit_reset"));
            } else {
                System.out.println("  Limit Reset: —");
            }
        }

        if (creditsInfo != null && creditsInfo.has("credits") && !creditsInfo.get("credits").isJsonNull()) {
            System.out.println("  Account Balance: $" + String.format("%.4f", creditsInfo.get("credits").getAsDouble()));
        } else {
            System.out.println("  Account Balance: (management key required)");
        }

        System.out.println("\nUsage");
        if (keyInfo != null) {
            if (keyInfo.has("usage") && !keyInfo.get("usage").isJsonNull()) {
                System.out.println("  Total: $" + String.format("%.4f", keyInfo.get("usage").getAsDouble()));
            }
            if (keyInfo.has("usage_daily") && !keyInfo.get("usage_daily").isJsonNull()) {
                System.out.println("  Daily: $" + String.format("%.4f", keyInfo.get("usage_daily").getAsDouble()));
            }
            if (keyInfo.has("usage_weekly") && !keyInfo.get("usage_weekly").isJsonNull()) {
                System.out.println("  Weekly: $" + String.format("%.4f", keyInfo.get("usage_weekly").getAsDouble()));
            }
            if (keyInfo.has("usage_monthly") && !keyInfo.get("usage_monthly").isJsonNull()) {
                System.out.println("  Monthly: $" + String.format("%.4f", keyInfo.get("usage_monthly").getAsDouble()));
            }
        }

        System.out.println("\nFree-tier Allowance");
        if (keyInfo != null) {
            if (keyInfo.has("is_free_tier") && !keyInfo.get("is_free_tier").isJsonNull()) {
                System.out.println("  Free Tier: " + keyInfo.get("is_free_tier").getAsBoolean());
            }
            if (keyInfo.has("free_model_daily_requests")) {
                JsonObject freeDaily = keyInfo.getAsJsonObject("free_model_daily_requests");
                if (freeDaily != null) {
                    if (freeDaily.has("used")) {
                        System.out.println("  Free Daily Used: " + freeDaily.get("used"));
                    }
                    if (freeDaily.has("limit")) {
                        System.out.println("  Free Daily Limit: " + freeDaily.get("limit"));
                    }
                    if (freeDaily.has("remaining")) {
                        System.out.println("  Free Daily Remaining: " + freeDaily.get("remaining"));
                    }
                }
            }
        }

        System.out.println("\nAccount Credits");
        if (keyInfo != null) {
            if (keyInfo.has("is_management_key") && !keyInfo.get("is_management_key").isJsonNull()) {
                System.out.println("  Management Key: " + keyInfo.get("is_management_key").getAsBoolean());
            }
            if (keyInfo.has("is_provisioning_key") && !keyInfo.get("is_provisioning_key").isJsonNull()) {
                System.out.println("  Provisioning Key: " + keyInfo.get("is_provisioning_key").getAsBoolean());
            }
            if (keyInfo.has("expires_at") && !keyInfo.get("expires_at").isJsonNull()) {
                System.out.println("  Expires At: " + keyInfo.get("expires_at"));
            } else {
                System.out.println("  Expires At: —");
            }
            if (keyInfo.has("allowed_data_regions")) {
                System.out.println("  Allowed Data Regions: " + keyInfo.get("allowed_data_regions"));
            }
        }
    }

    private static void simulateError(String errorType) throws ApiException {
        switch (errorType) {
            case "401" -> throw new ApiException("Authentication failed: Invalid or expired API key");
            case "429" -> throw new ApiException("Rate limited: Too many requests, please retry later");
            case "500" -> throw new ApiException("Server error: OpenRouter API is experiencing issues (HTTP 500)");
            default -> throw new ApiException("Unknown test error: " + errorType);
        }
    }

    private static String loadFromEnvFile() {
        try {
            String envFilePath = ".env";
            java.nio.file.Path path = java.nio.file.Paths.get(envFilePath);
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }

            String content = java.nio.file.Files.readString(path);
            for (String line : content.split("\n")) {
                if (line.startsWith("OPENROUTER_API_KEY=")) {
                    return line.substring("OPENROUTER_API_KEY=".length()).trim();
                }
            }
        } catch (Exception e) {
            // Silently ignore errors reading .env file
        }
        return null;
    }

    private static class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
