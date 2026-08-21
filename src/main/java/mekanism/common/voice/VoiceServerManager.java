package mekanism.common.voice;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceServerManager {

    private final Set<VoiceConnection> connections = ConcurrentHashMap.newKeySet();
    private ServerSocket serverSocket;
    private Thread listenThread;
    private volatile boolean foundLocal = false;
    private volatile boolean running;

    public void start() {
        Mekanism.logger.info("VoiceServer: Starting up server...");
        try {
            serverSocket = new ServerSocket(MekanismConfig.current().general.VOICE_PORT.val());
            running = true;
            (listenThread = new ListenThread()).start();
        } catch (Exception e) {
            running = false;
            Mekanism.logger.error("VoiceServer: Failed to start server.", e);
        }
    }

    public void stop() {
        try {
            Mekanism.logger.info("VoiceServer: Shutting down server...");
            if (listenThread != null) {
                listenThread.interrupt();
                listenThread = null;
            }
            foundLocal = false;
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception e) {
            Mekanism.logger.error("VoiceServer: Error while shutting down server.", e);
        }
        running = false;
        connections.clear();
    }

    public void removeConnection(VoiceConnection connection) {
        connections.remove(connection);
    }

    public boolean isFoundLocal() {
        return foundLocal;
    }

    public void setFoundLocal(boolean found) {
        foundLocal = found;
    }

    public void sendToPlayers(short byteCount, byte[] audioData, VoiceConnection connection) {
        if (connection.getPlayer() == null) {
            return;
        }
        int channel = connection.getCurrentChannel();
        if (channel == 0) {
            return;
        }
        for (VoiceConnection iterConn : connections) {
            if (iterConn.getPlayer() != null && iterConn != connection && iterConn.canListen(channel)) {
                iterConn.sendToPlayer(byteCount, audioData, connection);
            }
        }
    }

    private class ListenThread extends Thread {

        private ListenThread() {
            setDaemon(true);
            setName("VoiceServer Listen Thread");
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    VoiceConnection connection = new VoiceConnection(s);
                    connection.start();
                    connections.add(connection);
                    Mekanism.logger.info("VoiceServer: Accepted new connection.");
                } catch (SocketException | NullPointerException ignored) {
                } catch (Exception e) {
                    Mekanism.logger.error("VoiceServer: Error while accepting connection.", e);
                }
            }
        }
    }
}
