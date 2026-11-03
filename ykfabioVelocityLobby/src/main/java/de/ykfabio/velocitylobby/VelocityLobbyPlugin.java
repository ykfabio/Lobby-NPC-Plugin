package de.ykfabio.velocitylobby;

import com.google.inject.Inject;
import com.velocitypowered.api.command.*;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.velocitypowered.api.proxy.server.RegisteredServer;

@Plugin(id="ykfabiovelocitylobby", name="ykfabioVelocityLobby", version="1.0.0", description="Network-wide /lobby for ykfabio Network", authors={"ykfabio"})
public final class VelocityLobbyPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private String lobbyServer = "lobby";
    @Inject public VelocityLobbyPlugin(ProxyServer server, Logger logger, @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) { this.server=server; this.logger=logger; this.dataDirectory=dataDirectory; }
    @Subscribe public void onInit(ProxyInitializeEvent event) {
        try { Files.createDirectories(dataDirectory); Path file=dataDirectory.resolve("config.properties"); if(!Files.exists(file)){Files.writeString(file,"lobby-server=lobby\n",StandardOpenOption.CREATE);} Properties p=new Properties(); try(Reader r=Files.newBufferedReader(file)){p.load(r);} lobbyServer=p.getProperty("lobby-server","lobby"); } catch(IOException ex){logger.error("Config konnte nicht geladen werden",ex);}
        CommandMeta meta=server.getCommandManager().metaBuilder("lobby").plugin(this).build();
        server.getCommandManager().register(meta, new LobbyCommand());
        logger.info("ykfabioVelocityLobby aktiv. Zielserver: {}",lobbyServer);
    }
    private final class LobbyCommand implements SimpleCommand {
        @Override public void execute(Invocation invocation) {
            if(!(invocation.source() instanceof Player player)){invocation.source().sendMessage(Component.text("Dieser Befehl ist nur für Spieler."));return;}
            Optional<RegisteredServer> target=server.getServer(lobbyServer);
            if(target.isEmpty()){player.sendMessage(Component.text("§cDer Lobby-Server '"+lobbyServer+"' ist nicht in velocity.toml eingetragen."));return;}
            if(player.getCurrentServer().map(c->c.getServer().getServerInfo().getName().equalsIgnoreCase(lobbyServer)).orElse(false)){player.sendMessage(Component.text("§eDu bist bereits in der Lobby."));return;}
            player.createConnectionRequest(target.get()).connect().thenAccept(result->{if(!result.isSuccessful())player.sendMessage(Component.text("§cVerbindung zur Lobby fehlgeschlagen: "+result.getStatus().name()));});
        }
        @Override public List<String> suggest(Invocation invocation){return List.of();}
    }
}
