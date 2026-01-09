package bgu.spl.net.impl.stomp;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<StompFrame> {

     private byte[] bytes = new byte[1 << 10]; 
    private int len = 0;

    @Override
    public StompFrame decodeNextByte(byte nextByte) {
        if (nextByte != '\u0000') {
            pushByte(nextByte);
            return null; 
        }

        String fullFrame = popString();
        String[] lines = fullFrame.split("\n");
        if (lines.length == 0) 
            return null;
        String command = lines[0]; 
        Map<String, String> headers = new HashMap<>();
        int i = 1;
        while (i < lines.length && !lines[i].isEmpty()) {
            String line = lines[i];
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
            i++;
        }
        StringBuilder bodyBuilder = new StringBuilder();
        i++; 
        while (i < lines.length) {
            bodyBuilder.append(lines[i]);
            if (i < lines.length - 1) {
                bodyBuilder.append("\n");
            }
            i++;
        }
        return new StompFrame(command, headers, bodyBuilder.toString());
    }

    @Override
    public byte[] encode(StompFrame frame) {
        return frame.toStompString().getBytes(StandardCharsets.UTF_8);
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private String popString() {
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }
}
