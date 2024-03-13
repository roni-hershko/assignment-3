package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;



public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    int connectionId;
    String fileNameString;
    private final int packetSize = 512;
    String userName;
	short blockNum = 1;
	byte[] fileToSend;
	List<Byte> dataToSave = new ArrayList<>();
	Connections<byte[]> connections; 



    public TftpProtocol(){
		//insert all the files from the flies folder in the server into the fileMap
		String folderPath = "Flies" + File.separator;
		File folder = new File(folderPath);
        File[] files = folder.listFiles();
		ConcurrentHashMap<String, File> filesMap = new ConcurrentHashMap<>(); 

        if (files != null) {
            for (File file : files) {
					filesMap.put(file.getName(), file);
				}
            }
        
        Holder.fileMap = filesMap; 
    }
	

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
		this.connectionId = connectionId;
		this.connections = connections;

    }


    @Override
    public void process(byte[] message) {
        short message0 = (short)(message[0] & 0xff);
        if(message0 == 1)
        {
            //RRQ
            oneRRQ(message);
        }
        else if(message0 == 2)
        {
            //WRQ
            twoWRQ(message);
        }
        else if(message0 == 3)
        {
            //DATA
            threeDATARecive( message);
        }
        else if(message0 == 4)
        {
            //ACK
            fourACKRecive(message);        
        }
        else if(message0 == 6)
        {
            //DIRQ
            sixDIRQ(message);
        }
        else if(message0 == 7)
        {
            //LOGRQ
            sevenLOGRQ(message);
        }
        else if(message0 == 8)
        {
            //DELRQ
            eightDELRQ(message);
        }
        else if(message0 == 10)
        {
            //DISC  
            tenDISC(message);
        }
        else
        {
            //ERROR 0
            connections.send(connectionId, ERRORSend(0));
        }
    }


    @Override
    public boolean 
    shouldTerminate() {
        if(shouldTerminate){
            this.connections.disconnect(connectionId);
            Holder.logedInUserNames.remove(connectionId);
        }
        return shouldTerminate;
    }


    private void oneRRQ(byte[] message){
        int errNum = -1;
        boolean fileFound = false;
        
        //if the user is not logged in
        if(!Holder.logedInUserNames.containsKey(connectionId)){
            errNum = 6;
        }
        
        //convert the byte array to a string
        String fileName = new String(message, 1, message.length -1, StandardCharsets.UTF_8);
		String folderPath = "Flies" + File.separator;	
		Path filePath = Paths.get(folderPath,fileName);
		if(Files.exists(filePath)){
			fileFound = true;
			if(errNum == -1){
				try {
					fileToSend = Files.readAllBytes(filePath);
					byte[] dataPacket = DATASend(blockNum, fileToSend,0); 
					connections.send(connectionId, dataPacket);                            
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if(!fileFound){
			errNum = 1;
		}
	
		if(errNum != -1){
			connections.send(connectionId, ERRORSend(errNum));
		}
    }


    private void twoWRQ(byte[] message){
        int errNum = -1;

        //if the user is not logged in
        if(!Holder.logedInUserNames.containsKey(connectionId)){
            errNum = 6;
        }
		String fileName = new String(message, 1, message.length -1, StandardCharsets.UTF_8);
		fileNameString = fileName;
		String folderPath = "Flies" + File.separator;	
		Path filePath = Paths.get(folderPath,fileName);
		if(Files.exists(filePath)){
			errNum = 5;
		}

        if(errNum != -1){
			connections.send(connectionId, ERRORSend(errNum));
        }
        else {
			connections.send(connectionId, ACKSend((short)0));
			// create a file with the name of the file to upload in the files folder in the server
			File file = new File(folderPath,fileName);
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


    private void threeDATARecive(byte[] message){ 
		short DATAblockNum = byteToShort(message, 3,4);
		String folderPath = "Flies" + File.separator;	
		Path filePath = Paths.get(folderPath, fileNameString);
		
		for(int i = 5; i < message.length; i++){
			dataToSave.add(message[i]);
		}        

		connections.send(connectionId, ACKSend(DATAblockNum));

        if(message.length < packetSize){ 
			try { 
				byte[] fileBytes = new byte[dataToSave.size()];
				for (int i = 0; i < dataToSave.size(); i++) {
					fileBytes[i] = dataToSave.get(i);
				}
				Files.write(filePath, fileBytes);
				dataToSave.clear();
				Holder.fileMap.put(fileNameString, new File(folderPath, fileNameString));
				nineSendBroadcast( fileNameString.getBytes() ,1);
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }


    private void fourACKRecive(byte[] message){
		short ACKblockNum = byteToShort(message, 1,2); 
		blockNum++;
		//if there is more data packets to send
		if(fileToSend.length > packetSize*ACKblockNum){
			byte[] dataPacket = DATASend(blockNum, fileToSend, packetSize*(blockNum-1));
			connections.send(connectionId, dataPacket);
		}
		//if the file has been sent
		else{
			blockNum = 1;
			fileToSend = null;
		}
    }


    private void sixDIRQ(byte[] message){ 
		//if the user is not logged in
        if(!Holder.logedInUserNames.containsKey(connectionId)){
            ERRORSend(6);
        }
        else{
            String allFileNames= "";
            for (String key : Holder.fileMap.keySet()) {
                allFileNames += key + '\0'; 
            } 
            fileToSend = allFileNames.getBytes();
            byte[] dataPacket = DATASend(blockNum, fileToSend, 0);
			connections.send(connectionId, dataPacket);
        }
    }


    private void sevenLOGRQ(byte[] message)	{  

        int errNum = -1;
        userName = new String(message, 1, message.length-1, StandardCharsets.UTF_8);
        //if the user is already logged in
        if(Holder.logedInUserNames.containsKey(connectionId)){ 
            errNum = 7;
        }
        else{
            //if the user name is taken
            if(Holder.logedInUserNames.containsValue(userName)){
                //user name not valid
                errNum = 0;
            }
            else if(errNum == -1){ 
                //add the user to the connectionsMap
                Holder.logedInUserNames.put(connectionId, userName);
                //start(connectionId, connections); 
                connections.send(connectionId, ACKSend((short)0));
            } 
        }
        if(errNum != -1){
            connections.send(connectionId, ERRORSend(errNum));
    }
}


    private void eightDELRQ(byte[] message){
        int errNum = -1;
		String fileName = new String(message, 1,message.length-1, StandardCharsets.UTF_8);
		fileNameString = fileName;
        //if the user is not logged in
        if(!Holder.logedInUserNames.containsKey(connectionId)){
            errNum = 6;
        }
        //loged in
        else{
            boolean fileFound = false;

            for (String key : Holder.fileMap.keySet()) {
                if (key.equals(fileName)) {
                    Holder.fileMap.remove(key);
                    fileFound = true;
					//delete the file from the server
					File file = new File("Flies" + File.separator+fileName);
					file.delete();

                    break; // Exit the loop once the file is found
                }
            }
            if(!fileFound){
                errNum = 1;
            }
        }
        if(errNum != -1){
            connections.send(connectionId, ERRORSend(errNum));
        }
        else{
            connections.send(connectionId, ACKSend((short)0));         
            nineSendBroadcast( fileName.getBytes() ,0); 
        }
    }


    private void tenDISC(byte[] message){
		//if the user is not logged in
        if(!Holder.logedInUserNames.containsKey(connectionId)){
            connections.send(connectionId,ERRORSend(6));
        }
        else{
            shouldTerminate = true;
            connections.send(connectionId, ACKSend((short)0));
            shouldTerminate();
        }
    }


    private void nineSendBroadcast( byte[] fileNametoUploadOrDelete ,int flag){ //0 for delete 1 for added
        byte[] announce = new byte[fileNametoUploadOrDelete.length + 4]; 
        announce[0] = (byte)0;
        announce[1] = (byte)9;  
        announce[2] = (byte)flag;
        for(int i = 0; i < fileNametoUploadOrDelete.length; i++){
            announce[i+3] = fileNametoUploadOrDelete[i];
        }
        announce[announce.length-1] = 0;

        for(int i = 0; i < Holder.logedInUserNames.size(); i++){
                connections.send(i, announce);
        }
    }


    private byte[] ERRORSend(int numError){
        String err0= "Not defined, see error message (if any).";
        String err1= "File not found -RRQ, DELRQ of non existing file";
        String err2= "Access violation.";
        String err3= "Disk full or allocation exceeded.";
        String err4= "Illegal TFTP operation.";
        String err5= "File already exists- file name exists for WRQ.";
        String err6= "User not logged in -any opcode received before Login completes.";
        String err7= "User already logged in -Login username already connected.";

        //convert string to byte array
        byte[] error0 = err0.getBytes();
        byte[] error1 = err1.getBytes();
        byte[] error2 = err2.getBytes();
        byte[] error3 = err3.getBytes();
        byte[] error4 = err4.getBytes();
        byte[] error5 = err5.getBytes();
        byte[] error6 = err6.getBytes();
        byte[] error7 = err7.getBytes();
        
        //create the error response in the size of the error message and insert the message 
        byte[] ERRORSend;
        if(numError == 0){
			ERRORSend = new byte[5 + error0.length];
			for(int i = 0; i < error0.length; i++){
				ERRORSend[i+4] = error0[i];
			}
		}
        else if(numError == 1){
			ERRORSend = new byte[5 + error1.length];
			for(int i = 0; i < error1.length; i++){
				ERRORSend[i+4] = error1[i];
			}
		}
        else if(numError == 2){
            ERRORSend = new byte[5 + error2.length];
			for(int i = 0; i < error2.length; i++){
				ERRORSend[i+4] = error2[i];
			}
		}
        else if(numError == 3){
            ERRORSend = new byte[5 + error3.length];
			for(int i = 0; i < error3.length; i++){
				ERRORSend[i+4] = error3[i];
			}
		}
        else if(numError == 4){
            ERRORSend = new byte[5 + error4.length];
			for(int i = 0; i < error4.length; i++){
				ERRORSend[i+4] = error4[i];
			}
		}
        else if(numError == 5){
            ERRORSend = new byte[5 + error5.length];
			for(int i = 0; i < error5.length; i++){
				ERRORSend[i+4] = error5[i];
			}
		}
        else if(numError == 6){
            ERRORSend = new byte[5 + error6.length];
			for(int i = 0; i < error6.length; i++){
				ERRORSend[i+4] = error6[i];
			}
		}
        else{
			ERRORSend = new byte[5 + error7.length];
			for(int i = 0; i < error7.length; i++){
				ERRORSend[i+4] = error7[i];
			}

		}
        
        //insert values to the error response
        ERRORSend[0] = (byte)0;
        ERRORSend[1] = (byte)5;
        ERRORSend[2] = shortTobyte((short)numError)[0];
        ERRORSend[3] = shortTobyte((short)numError)[1];
        ERRORSend[ERRORSend.length-1] = (byte)0;
        return ERRORSend;
    }


    private byte[] ACKSend(short blockNum){ 
        byte[] ack = new byte[4];
        ack[0] = (byte)0; 
        ack[1] = (byte)4;
        ack[2] = shortTobyte(blockNum)[0];
        ack[3] = shortTobyte(blockNum)[1];
        return ack;
    }


    private byte[] DATASend(short blockNum, byte[] data , int indexData){
        //create data packet in the size of the packet remain to send
        int dataSectionSize = Math.min(packetSize, data.length - indexData);
        byte[] dataPacket = new byte[dataSectionSize + 6];

		dataPacket[0] = (byte)0;
		dataPacket[1] = (byte)3;

		byte[] dataSection = shortTobyte((short) dataSectionSize);
		dataPacket[2] = dataSection[0];
		dataPacket[3] = dataSection[1];

		byte[] blockNumber = shortTobyte((short)blockNum);
		dataPacket[4] = blockNumber[0];
		dataPacket[5] = blockNumber[1];

		for(int i = 6; i < dataPacket.length; i++){
			dataPacket[i] = data[indexData+i-6];
		}
		return dataPacket;
    }


    private byte[] shortTobyte(short a){
		return new byte []{(byte)(a >> 8) , (byte)(a & 0xff)};
	}
	
	
    private short byteToShort(byte[] byteArr, int fromIndex, int toIndex){
        return (short) ((((short)(byteArr[fromIndex]) & 0XFF)) << 8 | (short)(byteArr[toIndex] & 0XFF)); 
    }

}
