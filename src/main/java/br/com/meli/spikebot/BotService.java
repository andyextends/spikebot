package br.com.meli.spikebot;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Service;

@Service
public class BotService extends ListenerAdapter {
    @PostConstruct
    public void startBot() throws Exception {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente DISCORD_TOKEN com o token do bot.");
        }
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(this)
                .build();
        jda.awaitReady();
        System.out.println(" SpikeBot online!");
    }
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getMessage().getContentRaw().equalsIgnoreCase("!ping")) {
            event.getChannel().sendMessage("Pong!").queue();
        }
    }
}
