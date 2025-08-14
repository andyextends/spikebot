package br.com.meli.spikebot;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;   // <— INTENTS!
import org.springframework.stereotype.Service;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
public class BotService extends ListenerAdapter {
    private static final String PREFIX = "!sb";
    private static final Pattern DICE = Pattern.compile("(?:(\\d+))?d(\\d+)", Pattern.CASE_INSENSITIVE);
    private volatile JDA jda;
    private Instant startTime = Instant.now();
    // Opcional: restringir respostas a um canal específico (defina env ALLOWED_CHANNEL_ID com o ID)
    private final String allowedChannelId = System.getenv("ALLOWED_CHANNEL_ID");
    @PostConstruct
    public void startBot() throws Exception {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente DISCORD_TOKEN com o token do bot.");
        }
        // IMPORTANTE: habilitar intents (e ative MESSAGE CONTENT no Developer Portal)
        this.jda = JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .addEventListeners(this)
                .build();
        jda.awaitReady();
        startTime = Instant.now();
        System.out.println(":marca_de_verificação_branca: SpikeBot online! User: " + jda.getSelfUser().getAsTag());
    }
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        // Limita a um canal específico (se ALLOWED_CHANNEL_ID estiver definido)
        if (allowedChannelId != null && event.isFromGuild()) {
            if (!event.getChannel().getId().equals(allowedChannelId)) return;
        }
        String raw = event.getMessage().getContentRaw();
        if (!raw.startsWith(PREFIX)) return;
        String[] parts = raw.substring(PREFIX.length()).trim().split("\\s+", 2);
        String cmd  = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case "ping"   -> handlePing(event);
            case "help"   -> handleHelp(event);
            case "echo"   -> handleEcho(event, args);
            case "roll"   -> handleRoll(event, args);
            case "choose" -> handleChoose(event, args);
            case "avatar" -> handleAvatar(event, args);
            case "server" -> handleServer(event);
            default -> event.getChannel().sendMessage(":interrogação: Comando desconhecido. Use `!sb help`.").queue();
        }
    }
    /* ========================= Comandos ========================= */
    private void handlePing(MessageReceivedEvent event) {
        Instant start = Instant.now();
        event.getChannel().sendMessage(" Pong…").queue(msg -> {
            long api = jda.getGatewayPing();
            long rt  = Duration.between(start, Instant.now()).toMillis();
            Duration up = Duration.between(startTime, Instant.now());
            msg.editMessage(String.format(" Pong! API: %d ms | RT: %d ms | Uptime: %s",
                    api, rt, formatDuration(up))).queue();
        });
    }
    private void handleHelp(MessageReceivedEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Comandos do SpikeBot");
        eb.setColor(new Color(0x00B2FF));
        eb.setDescription("""
                **Prefixo:** `!sb`
                • `!sb ping` — mostra latência e uptime
                • `!sb help` — esta ajuda
                • `!sb echo <texto>` — repete a mensagem
                • `!sb roll [NdM]` — rola dados (ex: `!sb roll`, `!sb roll d20`, `!sb roll 3d6`)
                • `!sb choose a | b | c` — escolhe aleatoriamente
                • `!sb avatar [@user]` — mostra avatar seu ou de alguém marcado
                • `!sb server` — informações básicas do servidor
                """);
        if (allowedChannelId != null) eb.setFooter("Restringido ao canal ID " + allowedChannelId);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }
    private void handleEcho(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            event.getChannel().sendMessage("Uso: `!sb echo <texto>`").queue();
            return;
        }
        event.getChannel().sendMessage(args).queue();
    }
    private void handleRoll(MessageReceivedEvent event, String args) {
        int n = 1, faces = 6; // padrão 1d6
        if (!args.isBlank()) {
            Matcher m = DICE.matcher(args.trim());
            if (m.find()) {
                if (m.group(1) != null) n = clamp(parseInt(m.group(1), 1), 1, 100);
                faces = clamp(parseInt(m.group(2), 6), 2, 1000);
            } else {
                event.getChannel().sendMessage("Uso: `!sb roll`, `!sb roll d20`, `!sb roll 3d6`").queue();
                return;
            }
        }
        Random r = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(" **Roll** ").append(n).append("d").append(faces).append(": ");
        int sum = 0;
        sb.append("[");
        for (int i = 0; i < n; i++) {
            int v = r.nextInt(faces) + 1;
            sum += v;
            sb.append(v);
            if (i < n - 1) sb.append(", ");
        }
        sb.append("] = **").append(sum).append("**");
        event.getChannel().sendMessage(sb.toString()).queue();
    }
    private void handleChoose(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            event.getChannel().sendMessage("Uso: `!sb choose opção1 | opção2 | opção3`").queue();
            return;
        }
        String[] options = List.of(args.split("\\|")).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (options.length < 2) {
            event.getChannel().sendMessage("Forneça pelo menos duas opções separadas por `|`.").queue();
            return;
        }
        int pick = ThreadLocalRandom.current().nextInt(options.length);
        event.getChannel().sendMessage(" Escolhi: **" + options[pick] + "**").queue();
    }
    private void handleAvatar(MessageReceivedEvent event, String args) {
        User target = event.getAuthor();
        if (!args.isBlank() && !event.getMessage().getMentions().getUsers().isEmpty()) {
            target = event.getMessage().getMentions().getUsers().get(0);
        }
        String url = target.getEffectiveAvatarUrl() + "?size=512";
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Avatar de " + target.getAsTag());
        eb.setImage(url);
        eb.setColor(Color.ORANGE);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }
    private void handleServer(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("Este comando só está disponível em servidores.").queue();
            return;
        }
        var g = event.getGuild();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(" Informações do servidor");
        eb.setColor(Color.GREEN);
        eb.addField("Nome", g.getName(), true);
        eb.addField("ID", g.getId(), true);
        eb.addField("Membros", String.valueOf(g.getMemberCount()), true);
        Member self = g.getSelfMember();
        TextChannel defaultCh = g.getDefaultChannel() instanceof TextChannel ch ? ch : null;
        if (defaultCh != null) eb.addField("Canal padrão", "#" + defaultCh.getName(), true);
        eb.setThumbnail(g.getIconUrl());
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }
    /* ========================= Utils ========================= */
    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        long s = d.minusHours(h).minusMinutes(m).toSeconds();
        return String.format("%02dh%02dm%02ds", h, m, s);
    }
}
