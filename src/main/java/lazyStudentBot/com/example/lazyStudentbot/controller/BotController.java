package lazyStudentBot.com.example.lazyStudentbot.controller;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetChatPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
public class BotController {

    @Value("${telegram.bot.token}")
    private String telegramBotToken;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private TelegramBot bot;

    @PostConstruct
    public void start() {
        bot = new TelegramBot(telegramBotToken);
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    handleTextMessage(update);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void handleTextMessage(Update update) {
        String messageText = update.message().text();
        long chatId = update.message().chat().id();

        if (messageText.equals("/start")) {
            bot.execute(new SendMessage(chatId, "Welcome to Lazy Student Bot! Send me an image with questions."));
        } else if (messageText.equals("/help")) {
            bot.execute(new SendMessage(chatId, "Send an image with questions, and I'll help you."));
        } else {
            String response = processMessageWithOpenAI(messageText);
            bot.execute(new SendMessage(chatId, response));
        }
    }



    private String processMessageWithOpenAI(String messageText) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com")) // Updated endpoint
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject()
                            .put("model", "text-davinci-003") // Specify the model
                            .put("prompt", messageText)
                            .put("max_tokens", 50)
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
