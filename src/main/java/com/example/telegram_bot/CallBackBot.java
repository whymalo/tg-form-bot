package com.example.telegram_bot;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendContact;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;


@Slf4j
@Component
public class CallBackBot implements SpringLongPollingBot,
        LongPollingSingleThreadUpdateConsumer {

    private final Map<Long, UserState> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userData = new HashMap<>();

    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${app.manager.chat-id}")
    private Long managerChatId;

    private TelegramClient client;

    @PostConstruct
    private void init() {
        client = new OkHttpTelegramClient(botToken);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    private static final Pattern PHONE = Pattern.compile("^[+]?\\d[\\d\\s\\-]{7,}$");

    @Override
    public void consume(Update u) {
        if (u.hasCallbackQuery()) {
            String data = u.getCallbackQuery().getData();
            Message message = (Message) u.getCallbackQuery().getMessage();
            Long chatId = message.getChatId();
            Integer messageId = message.getMessageId();

            if ("mark_reviewed".equals(data)) {
                String oldText = message.getText();
                log.info("Old message text: {}", oldText);
                String newText = oldText.replace("Новая заявка!", "Рассмотренная заявка ✅");

                try {
                    client.execute(EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .text(newText)
                            .parseMode(ParseMode.MARKDOWN)
                            .replyMarkup(null)
                            .build());

                    client.execute(AnswerCallbackQuery.builder()
                            .callbackQueryId(u.getCallbackQuery().getId())
                            .text("Заявка отмечена ✅")
                            .showAlert(false)
                            .build());

                } catch (TelegramApiException e) {
                    log.error("Ошибка при редактировании сообщения", e);
                }
            }
            return;
        }

        if (!u.hasMessage() || !u.getMessage().hasText()) return;

        String txt = u.getMessage().getText().trim();
        Long chatId = u.getMessage().getChatId();

        if (txt.equals("/start")) {
            userStates.put(chatId, UserState.WAIT_NAME);
            userData.put(chatId, new HashMap<>());
            send(SendMessage.builder()
                    .chatId(chatId)
                    .text("Введите ваше *ФИО* полностью (например: Иванов Иван Иванович)")
                    .parseMode(ParseMode.MARKDOWN)
                    .build());
            return;
        }

        UserState state = userStates.getOrDefault(chatId, UserState.WAIT_NAME);

        switch (state) {
            case WAIT_NAME -> {
                if (isValidFullName(txt)) {
                    userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("name", txt);
                    userStates.put(chatId, UserState.WAIT_PHONE);
                    askPhone(chatId);
                } else {
                    send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Пожалуйста, введите *ФИО полностью* (например: Иванов Иван Иванович),\nвсе три слова с заглавной буквы.")
                            .parseMode(ParseMode.MARKDOWN)
                            .build());
                }
            }

            case WAIT_PHONE -> {
                String cleaned = txt.replaceAll("\\D+", "");
                if (PHONE.matcher(txt).matches() && cleaned.length() == 11) {
                    userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("phone", cleaned);
                    userStates.put(chatId, UserState.WAIT_BIRTHDATE);
                    send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Введите дату рождения в формате 01.01.2000")
                            .build());
                } else {
                    send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Пожалуйста, введите корректный номер телефона из *11 цифр* (например: 79001234567).")
                            .parseMode(ParseMode.MARKDOWN)
                            .build());
                }
            }

            case WAIT_BIRTHDATE -> {
                if (isValidAndAdult(txt)) {
                    userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("birthdate", txt);
                    userStates.put(chatId, UserState.WAIT_CITY);
                    send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Укажите ваш город:")
                            .build());
                } else {
                    send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Введите корректную дату. Вам должно быть 18 лет и более.")
                            .build());
                }
            }

            case WAIT_CITY -> {
                userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("city", txt);
                userStates.put(chatId, UserState.DONE);
                forwardFull(userData.get(chatId), chatId);
            }

            default -> {
                send(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите /start для начала заполнения анкеты.")
                        .build());
            }
        }
    }



    //проверка возраста
    private boolean isValidAndAdult(String input) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate birthDate = LocalDate.parse(input, formatter);
            return birthDate.isBefore(LocalDate.now().minusYears(18));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    //проверка ФИО: должно быть 3 слова и каждое с заглавной(можно латиницей). Пример Иванов Иван Иванович/Ivanov Ivan Ivanovich
    private boolean isValidFullName(String input) {
        return input.matches("^[A-ZА-ЯЁ][a-zа-яё]+\\s+[A-ZА-ЯЁ][a-zа-яё]+\\s+[A-ZА-ЯЁ][a-zа-яё]+$");
    }

    //
    private void askPhone(Long chat) {
        send(SendMessage.builder()
                .chatId(chat)
                .text("Пожалуйста, введите свой номер телефона в формате" + "\n7XXXXXXXXXX")
                .build());
    }

    private void retry(Long chat) {
        send(SendMessage.builder()
                .chatId(chat)
                .text("Не могу распознать номер, попробуйте ещё раз.")
                .build());
    }

    private void forwardFull(Map<String, String> data, Long userChat) {
        // 1 Сообщение менеджеру с возможностью отметить заявку, которую он уже рассмотрел путем нажатия ✅ Рассмотрено под самой заявкой
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("✅ Рассмотрено")
                .callbackData("mark_reviewed")
                .build();

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(List.of(button)));

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();

        SendMessage msg = SendMessage.builder()
                .chatId(managerChatId)
                .parseMode(ParseMode.MARKDOWN)
                .text("*Новая заявка!*\n" +
                        "ФИО: " + data.get("name") + "\n" +
                        "Телефон: `" + data.get("phone") + "`\n" +
                        "Дата рождения: " + data.get("birthdate") + "\n" +
                        "Город: " + data.get("city"))
                .replyMarkup(markup)
                .build();

        send(msg);

        // 2 Отправка контакта менеджеру
        send(SendContact.builder()
                .chatId(managerChatId)
                .phoneNumber(data.get("phone"))
                .firstName(data.get("name"))
                .build());

        // 3 Подтверждение пользователю
        send(SendMessage.builder()
                .chatId(userChat)
                .text("Спасибо! Менеджер скоро свяжется с вами. 📞")
                .build());
    }


    private void send(BotApiMethod<?> msg) {
        try {
            client.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Telegram error", e);
        }
    }
}
