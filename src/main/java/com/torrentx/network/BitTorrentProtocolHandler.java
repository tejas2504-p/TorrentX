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
        DataInputStream dataIn = new DataInputStream(in);
        
        int pstrlen = dataIn.readUnsignedByte();
        if (pstrlen != Handshake.PROTOCOL_IDENTIFIER.length()) {
            throw new ProtocolException("Invalid protocol length: " + pstrlen);
        }
        
        byte[] pstrBytes = new byte[pstrlen];
        dataIn.readFully(pstrBytes);
        String pstr = new String(pstrBytes, StandardCharsets.UTF_8);
        if (!Handshake.PROTOCOL_IDENTIFIER.equals(pstr)) {
            throw new ProtocolException("Unsupported protocol: " + pstr);
        }
        
        byte[] reserved = new byte[8];
        dataIn.readFully(reserved); // Skip reserved bytes
        
        byte[] infoHash = new byte[20];
        dataIn.readFully(infoHash);
        
        byte[] peerId = new byte[20];
        dataIn.readFully(peerId);
        
        return new Handshake(infoHash, peerId);
    }

    public void writeHandshake(Handshake handshake, OutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        
        byte[] pstrBytes = Handshake.PROTOCOL_IDENTIFIER.getBytes(StandardCharsets.UTF_8);
        dataOut.writeByte(pstrBytes.length);
        dataOut.write(pstrBytes);
        
        dataOut.write(new byte[8]); // Reserved bytes
        dataOut.write(handshake.getInfoHash());
        dataOut.write(handshake.getPeerId());
        
        dataOut.flush();
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
