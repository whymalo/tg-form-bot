Пошаговая анкета с проверками (ФИО, номер телефона, дата рождения)
Хранение состояний пользователя
Отправка данных администратору
Inline-кнопка "✅ Рассмотрено" для админа
Отправка контакта

- Java 17+
- Spring Boot
- TelegramBots (Long Polling)
- Lombok
- Maven

Быстрый старт:

1. Клонируйте репозиторий

```bash
git clone https://github.com/yourusername/telegram-form-bot.git
```
```bash
cd telegram-form-bot
```
2. Сгенерируйте токен у @BotFather в телеграм, получите ваш chat_id у @userinfobot.
3. Настройте конфигурацию application.properties (можно создать application.yml) папке src/main/resources
   Замените значения telegram.bot.token=YOUR_BOT_TOKEN и app.manager.chat-id=YOUR_TELEGRAM_CHAT_ID на полученные у ботов.
4. Запустите TelegramBotApplication или используйте
```bash
      ./mvnw spring-boot:run
```
   

Пример сценария работы:
1. Пользователь запускает бота с помощью команды /start
    
2. Бот запрашивает:
    
    - ФИО
        
    - Телефон (11 цифр)
        
    - Дату рождения (формат dd.MM.yyyy)
        
    - Город
        
3. После заполнения — бот пересылает данные администратору
    
4. Менеджер может нажать кнопку ✅ Рассмотрено, чтобы отметить анкету

Бот не использует бд, все сообщения присылаются менеджеру, там и хранятся

Структура:
```
telegram-form-bot/
├── src/main/java/com/example/telegram_bot/
│   ├── CallBackBot.java         # Логика бота
│   ├── UserState.java           # Enum состояний
|   |── TelegramBotApplication   # Запуск бота
├── src/main/resources/
│   └── application.yml          # Конфигурация
├── pom.xml                      # Зависимости
└── README.md                    # Описание
```
