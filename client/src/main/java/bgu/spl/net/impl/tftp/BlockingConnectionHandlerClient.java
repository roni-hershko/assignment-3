package bgu.spl.net.impl.tftp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

import bgu.spl.net.api.MessageEncoderDecoder;


public class BlockingConnectionHandlerClient implements Runnable {
    
   
    public final TftpProtocol protocol;
    public final TftpEncoderDecoder encdec;
    public final Socket sock;
    public BufferedInputStream in;
    public BufferedOutputStream out;
   

    public BlockingConnectionHandlerClient(Socket sock, TftpEncoderDecoder reader, TftpProtocol protocol, BufferedInputStream in, BufferedOutputStream out) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.in = in;
        this.out = out;
    }


    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] ansFromServer = encdec.decodeNextByte((byte) read);
                if (ansFromServer != null) {
                    if(protocol.waitingForUpload || protocol.waitingForData || protocol.waitingForDirq){
                        send(protocol.process(ansFromServer));
                    }
                    else{
                        protocol.process(ansFromServer);
                    }
                }
            }
            } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    public void send(byte[] msg) {
        try{
            if (msg != null) {
                out.write(encdec.encode(msg));
                out.flush();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}



