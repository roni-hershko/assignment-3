package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;

public class KeyBoardConnectionHandler implements Runnable {
    private final TftpProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private final Socket sock;
    private BufferedReader keyboard;
    private BufferedOutputStream out;
   

    
    public KeyBoardConnectionHandler(BlockingConnectionHandlerClient handler, BufferedOutputStream out, BufferedReader keyboard) {
        this.sock = handler.sock;
        this.encdec = handler.encdec;
        this.protocol = handler.protocol;
        this.out = out;
        this.keyboard = keyboard;
    }

    public void run() {
        try {
            while (!protocol.shouldTerminate()){
                String line = keyboard.readLine();	
                byte[] lineToByte = protocol.creatRequest(line);
                if(lineToByte != null){ //if the request is valid
                    out.write((encdec.encode(lineToByte)));
                    out.flush();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }			
	}
}
