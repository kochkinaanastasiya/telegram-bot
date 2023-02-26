package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.NotificationTaskRepository;
import pro.sky.telegrambot.model.NotificationTask;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private TelegramBot telegramBot;
    private NotificationTaskRepository notificationTaskRepository;

    private final static String MESSAGE = "Hello, dear friend! Welcome to the chat bot!";
    private final static String PATTERN = "([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)";
    private final static String DATA_TIME_FORMAT = "dd.MM.yyyy HH:mm";

    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskRepository notificationTaskRepository) {
        this.telegramBot = telegramBot;
        this.notificationTaskRepository = notificationTaskRepository;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            if(update.message() == null){
                return;
            }
            logger.info("Processing update: {}", update);

            String msgText = update.message().text();
            long chatId = update.message().chat().id();

            // Welcome message
            if(msgText.equals("/start")) {
                sendMessage(chatId, MESSAGE);
            } else {
                Pattern pattern = Pattern.compile(PATTERN);
                Matcher matcher = pattern.matcher(msgText);
                if (matcher.matches()) {
                    String date = matcher.group(1);
                    String message = matcher.group(3);
                    LocalDateTime localDateTime = LocalDateTime.parse(date, DateTimeFormatter.ofPattern(DATA_TIME_FORMAT));
                    logger.info("Message of notification task (date: {}, message: {})", localDateTime, message);
                    notificationTaskRepository.save(new NotificationTask(chatId, message, localDateTime));
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }
    @Scheduled(cron = "0 0/1 * * * *")
    public void setNotificationTask() {
        //findAllTasksByDateTime
        Collection<NotificationTask> currentTask = notificationTaskRepository.getByNotificationDate(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        currentTask.forEach(task -> sendMessage(task.getChatId(), task.getMessage()));

    }
    public void sendMessage(long chatId, String messageText){
        SendMessage message = new SendMessage(chatId, messageText);
        SendResponse response = telegramBot.execute(message);
        if(!response.isOk()){
            logger.warn("Message didn't sent: {}, error code: {}", message, response.errorCode());
        }
    }
}

