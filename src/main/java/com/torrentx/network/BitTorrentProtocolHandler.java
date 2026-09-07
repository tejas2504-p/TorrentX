package com.torrentx.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BitTorrentProtocolHandler implements ProtocolHandler {

    private static final int MAX_MESSAGE_SIZE = 32 * 1024; // 32KB max payload length

    public Handshake readHandshake(InputStream in) throws IOException {
        return HandshakeDecoder.decode(in);
    }

    public void writeHandshake(Handshake handshake, OutputStream out) throws IOException {
        out.write(HandshakeEncoder.encode(handshake));
        out.flush();
    }

    @Override
    public Message readMessage(InputStream in) throws Exception {
        DataInputStream dataIn = new DataInputStream(in);
        
        int length = dataIn.readInt();
        if (length < 0 || length > MAX_MESSAGE_SIZE) {
            throw new ProtocolException("Invalid message length: " + length);
        }
        
        if (length == 0) {
            return Message.keepAlive();
        }
        
        byte id = dataIn.readByte();
        byte[] payload = new byte[length - 1];
        if (payload.length > 0) {
            dataIn.readFully(payload);
        }
        
        return new Message(id, payload);
    }

    @Override
    public void writeMessage(Message message, OutputStream out) throws Exception {
        DataOutputStream dataOut = new DataOutputStream(out);
        
        if (message.isKeepAlive()) {
            dataOut.writeInt(0);
        } else {
            byte[] payload = message.getPayload() != null ? message.getPayload() : new byte[0];
            dataOut.writeInt(1 + payload.length);
            dataOut.writeByte(message.getId());
            dataOut.write(payload);
        }
        dataOut.flush();
    }
}
