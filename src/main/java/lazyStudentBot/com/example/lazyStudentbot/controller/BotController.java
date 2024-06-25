package lazyStudentBot.com.example.lazyStudentbot.controller;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetFileResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class BotController {

    @Value("${telegram.bot.token}")
    private String telegramBotToken;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${apininja.api.key}")
    private String apiNinjaApiKey;

    private TelegramBot bot;

    @PostConstruct
    public void start() {
        bot = new TelegramBot(telegramBotToken);
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null) {
                    if (update.message().text() != null) {
                        handleTextMessage(update);
                    } else if (update.message().photo() != null) {
                        handlePhotoMessage(update);
                    }
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

    private void handlePhotoMessage(Update update) {
        long chatId = update.message().chat().id();
        PhotoSize[] photos = update.message().photo();
        PhotoSize photo = photos[photos.length - 1]; // Get the highest resolution photo
        GetFile request = new GetFile(photo.fileId());
        GetFileResponse getFileResponse = bot.execute(request);
        String filePath = getFileResponse.file().filePath();

        try {
            String fileUrl = "https://api.telegram.org/file/bot" + telegramBotToken + "/" + filePath;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .build();
            HttpResponse<Path> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofFile(Path.of("temp")));

            File imageFile = response.body().toFile();
            String extractedText = processImageWithApiNinjas(imageFile);
            if (extractedText != null) {
                String aiResponse = processMessageWithOpenAI(extractedText);
                bot.execute(new SendMessage(chatId, aiResponse));
            } else {
                bot.execute(new SendMessage(chatId, "Sorry, I couldn't extract text from the image."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            bot.execute(new SendMessage(chatId, "Sorry, I couldn't process the image."));
        }
    }

    private String processImageWithApiNinjas(File imageFile) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofFile(imageFile.toPath());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.api-ninjas.com/v1/imagetotext"))
                    .header("X-Api-Key", apiNinjaApiKey)
                    .header("Content-Type", "multipart/form-data")
                    .POST(bodyPublisher)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            if (jsonResponse.has("text")) {
                return jsonResponse.getString("text");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String processMessageWithOpenAI(String messageText) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject()
                            .put("model", "gpt-3.5-turbo")
                            .put("messages", new JSONArray().put(new JSONObject()
                                    .put("role", "user")
                                    .put("content", messageText)))
                            .put("temperature", 0.7)
                            .toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "Sorry, I couldn't process your request.";
        }
    }
}
