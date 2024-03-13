package bgu.spl.net.impl.tftp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

	
	public class TftpClient {
	
		public static void main(String[] args) throws IOException {
			if (args.length == 0) {
				args = new String[]{"localhost"};
			}
			try (Socket sock = new Socket(args[0], 7777);
					BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
					BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())){
			System.out.println("connected to server");
			BlockingConnectionHandlerClient handler = new BlockingConnectionHandlerClient(sock, new TftpEncoderDecoder(), new TftpProtocol(), in, out);
			Thread handlThread = new Thread(handler);
			handlThread.start();
			
			KeyBoardConnectionHandler keyboardHandler = new KeyBoardConnectionHandler(handler, out, new BufferedReader(new java.io.InputStreamReader(System.in)));		
			Thread keyboardThread = new Thread(keyboardHandler);
			keyboardThread.start();

			try{
				handlThread.join();
				keyboardThread.interrupt();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
	