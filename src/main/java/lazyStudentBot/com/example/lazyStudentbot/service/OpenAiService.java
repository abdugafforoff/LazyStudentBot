package lazyStudentBot.com.example.lazyStudentbot.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class OpenAiService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    public String getOpenAiResponse(String prompt) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/engines/davinci-codex/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject()
                            .put("prompt", prompt)
                            .put("max_tokens", 100)
                            .toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            return jsonResponse.getJSONArray("choices").getJSONObject(0).getString("text").trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "Sorry, I couldn't process your request.";
        }
    }
}
