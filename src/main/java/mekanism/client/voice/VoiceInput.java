package mekanism.client.voice;

import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

@SideOnly(Side.CLIENT)
public class VoiceInput extends Thread {

    private VoiceClient voiceClient;
    private DataLine.Info microphone;
    private TargetDataLine targetLine;

    public VoiceInput(VoiceClient client) {
        voiceClient = client;
        microphone = new DataLine.Info(TargetDataLine.class, voiceClient.getAudioFormat(), 2200);

        setDaemon(true);
        setName("VoiceServer Client Input Thread");
    }

    @Override
    public void run() {
        try {
            if (!AudioSystem.isLineSupported(microphone)) {
                Mekanism.logger.info("No audio system available.");
                return;
            }
            targetLine = (TargetDataLine) AudioSystem.getLine(microphone);
            targetLine.open(voiceClient.getAudioFormat(), 2200);
            targetLine.start();
            AudioInputStream audioInput = new AudioInputStream(targetLine);
            byte[] audioData = new byte[2200];

            boolean doFlush = false;

            while (voiceClient.isRunning()) {
                if (MekanismKeyHandler.voiceKey.isPressed()) {
                    targetLine.flush();

                    while (voiceClient.isRunning() && MekanismKeyHandler.voiceKey.isPressed()) {
                        try {
                            int availableBytes = audioInput.available();
                            if (availableBytes <= 0) {
                                Thread.sleep(5L);
                                continue;
                            }
                            int bytesRead = audioInput.read(audioData, 0, Math.min(availableBytes, audioData.length));

                            if (bytesRead > 0) {
                                voiceClient.getOutputStream().writeShort(bytesRead);
                                voiceClient.getOutputStream().write(audioData, 0, bytesRead);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    try {
                        Thread.sleep(200L);
                    } catch (Exception ignored) {
                    }

                    doFlush = true;
                } else if (doFlush) {
                    try {
                        voiceClient.getOutputStream().flush();
                    } catch (Exception ignored) {
                    }

                    doFlush = false;
                }

                try {
                    Thread.sleep(20L);
                } catch (Exception ignored) {
                }
            }

            audioInput.close();
        } catch (Exception e) {
            Mekanism.logger.error("VoiceServer: Error while running client input thread.", e);
        }
    }

    public void close() {
        if (targetLine != null) {
            targetLine.flush();
            targetLine.close();
        }
    }
}
