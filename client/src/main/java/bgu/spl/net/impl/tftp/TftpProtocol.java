package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TftpProtocol implements MessagingProtocol<byte[]>{

    private static final byte RRQ_OPCODE = 1; 
    private static final byte WRQ_OPCODE = 2;
    private static final byte DIRQ_OPCODE = 6;
    private static final byte LOGRQ_OPCODE = 7;
    private static final byte DELRQ_OPCODE = 8;
    private static final byte DISC_OPCODE = 10;

    Map<String, File> myFiles = new HashMap<>();
    private boolean shouldTerminate = false;
    boolean waitingForDirq = false;
    boolean waitingForData = false;
    boolean waitingForUpload = false;
    private final int packetSize = 512;
    private String fileNameToUpload = "";
    private String fileNameToDownload = "";
    private int blockNum = 1;
    private byte[] dirqData = new byte[0];
    byte[] fileToSend;
    List<Byte> dataToSave = new ArrayList<>();



    public byte[] process(byte[] msg) {
        short message0 = (short)(msg[0] & 0xff); 
        int packetSizeofData=0;
        //the size of the packet
        if(msg.length>2){
            packetSizeofData = byteToShort(msg, 1, 2);
        }
        if(message0 == 3) //data
        {
            if(waitingForDirq)
            {
                dirqData = addDataToDirq(msg, dirqData);
                if(packetSizeofData < packetSize){
                    printDirq(msg);
                    waitingForDirq = false;
                    dirqData = new byte[0];
                }
                return null;
            }
            else
            {
                int blockNumFromData = byteToShort(msg, 3, 4);
                addContentToFile(msg, blockNumFromData);
                return ACKSend((short)blockNumFromData);
            }
            
        }
        else if(message0 == 4) //ack
        {
            int ackNum = byteToShort(msg, 1, 2);
            System.out.println("ACK " + ackNum);
            blockNum = ackNum+1;
            if(waitingForUpload){
                return sendFile(blockNum, packetSize*(ackNum));
            }
            else{
                waitingForUpload = false;
                blockNum = 1;
                fileNameToUpload = "";
                fileToSend = new byte[0];
            }
            return null;
        }
        else if(message0 == 5) //Error
        {
            waitingForDirq = false;
            if(waitingForUpload){
                File file = new File("." + File.separator + fileNameToUpload);
                file.delete();
            }
            waitingForUpload = false;
            blockNum = 0;
            if(waitingForData){
                File file = new File("." + File.separator+fileNameToDownload);
                file.delete();
            }
            waitingForData = false;
            fileNameToDownload = "";
            fileNameToUpload = "";

            ERROR(msg);
            return null;
        }
        else if(message0 ==9)//broadcast
        {            
            short message1 = (short)(msg[1] & 0xff); 
            if(message1== 0)
                System.out.println("BCAST: del " + new String(msg, 2, msg.length-2, StandardCharsets.UTF_8));
            else
                System.out.println("BCAST: add " + new String(msg, 2, msg.length-2, StandardCharsets.UTF_8));

            return null;
        }
        else if(message0 == 10)//disc
        {
            shouldTerminate = true;
            return null;
        }
       
        return null;
    }

    public byte[] creatRequest(String message) {

        String[] parts = splitBySpace(message);


        // Determine opcode and data
        byte opcode = 0; // Default to invalid opcode
        byte[] dataBytes = null;
        if(parts.length == 1){

            String command = parts[0];
            switch (command) {
                case "DIRQ":
                    opcode = DIRQ_OPCODE;
                    waitingForDirq = true;
                    break;
                case "DISC":
                    opcode = DISC_OPCODE;
                    break;
                default:
                    selfError(0);
            }
        }
        else if (parts.length == 2) {
            String command = parts[0];
            String data = parts[1];
            switch (command) {
                case "RRQ":
                    if(!isFileExist(data)){
                        dataBytes = data.getBytes(StandardCharsets.UTF_8);
                        fileNameToDownload = data;
                        waitingForData = true;
                        opcode = RRQ_OPCODE;
                        createFile();
                    }
                    else{
                        selfError(5);
                    }
                    break;
                case "WRQ":
                    if(isFileExist(data)){
                        dataBytes = data.getBytes(StandardCharsets.UTF_8);
                        fileNameToUpload = data;
                        waitingForUpload =true;
                        opcode = WRQ_OPCODE;
                    }
                    else{
                        selfError(1);
                    }
                    break;
                case "LOGRQ":
                    opcode = LOGRQ_OPCODE;
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                    break;
                case "DELRQ":
                    opcode = DELRQ_OPCODE;
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                    break;

                default:
                    selfError(0);
            }
        } else {
            selfError(0);
        }

        
        if(opcode!=0){

            if(opcode == RRQ_OPCODE || opcode == WRQ_OPCODE || opcode == LOGRQ_OPCODE || opcode == DELRQ_OPCODE){

                byte[] messageBytes = new byte[dataBytes.length + 3];
                messageBytes[0] = (byte) 0;
                messageBytes[1] = opcode;
                for (int i = 0; i < dataBytes.length; i++) {
                    messageBytes[i + 2] = dataBytes[i];
                }
                messageBytes[messageBytes.length - 1] = 0;
                return messageBytes;
            }
            else //opcode == DIRQ_OPCODE || opcode == DISC_OPCODE)
            {
                byte[] messageBytes = new byte[2];
                messageBytes[0] = (byte) 0;
                messageBytes[1] = opcode;
                return messageBytes;
            }
        }
        else
            return null;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
   
    public void ERROR(byte[] msg) {
        String errorMsg = new String(msg, 3, msg.length-1-3, StandardCharsets.UTF_8);
		String errorNumStr = String.valueOf(msg[2]);
        System.out.println("Error: "  + errorNumStr + " " + errorMsg);
    }

    public void printDirq(byte[] msg) {
        int lastIndex=5;
        while(lastIndex < msg.length){
            int firstIndex = lastIndex;
            while(lastIndex < msg.length && ((short)(msg[lastIndex] & 0xff) != 0)){
                lastIndex++;
            }
            String fileNameDirq = new String(msg, firstIndex, lastIndex-firstIndex);
            System.out.println(fileNameDirq);
            lastIndex++;
        }
    }

    public void createFile(){

        String directoryPath = "." + File.separator;
        File file = new File(directoryPath + fileNameToDownload);

        try {
            // Create the file
            if (file.createNewFile()) {
            } else {
                selfError(0);
            }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public void addContentToFile(byte[] msg, int blockNum) {

		String folderPath = "." + File.separator;	
		Path filePath = Paths.get(folderPath,fileNameToDownload);
		
		for(int i = 5; i < msg.length; i++){
			dataToSave.add(msg[i]);
		}  
        
        if(msg.length < packetSize){ 
            try { 
                byte[] fileBytes = new byte[dataToSave.size()];
                for (int i = 0; i < dataToSave.size(); i++) {
                    fileBytes[i] = dataToSave.get(i);
                }
                Files.write(filePath, fileBytes);
                dataToSave.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("RRQ " + fileNameToDownload + " Complete");
            waitingForData = false;
            fileNameToDownload = "";
            blockNum = 0;
        }
    }

    public byte[] sendFile(int blockNum, int indexData){
        String folderPath =  "." + File.separator;	
        Path filePath = Paths.get(folderPath,fileNameToUpload);
        if(Files.exists(filePath)){
            try {
                fileToSend = Files.readAllBytes(filePath);
                byte[] dataPacket = opcodeDATA(blockNum, fileToSend, indexData); 
               
                if(dataPacket.length <= packetSize + 5){
                    waitingForUpload = false;
                    blockNum = 1;
                    System.out.println("WRQ "+fileNameToUpload+" complete"); 
                    fileNameToUpload = "";
                }
                return dataPacket;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            selfError(1);
        }
        return null;
    }
    
    private byte[] opcodeDATA(int blockNum, byte[] data , int indexData){

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
        indexData = indexData + dataSectionSize;
		return dataPacket;
    } 
        
    private boolean isFileExist(String fileName){
        File file = new File("." + File.separator+fileName);
        return file.exists();
    }
    
    private void selfError(int errNum){
        String err0= "Not defined, see error message (if any).";
        String err1= "File does not exist.";
        String err2= "Access violation.";
        String err3= "Disk full or allocation exceeded.";
        String err4= "Illegal TFTP operation.";
        String err5= "File already exists";
        String err6= "User not logged in ";
        String err7= "User already logged in ";
        if(errNum == 0)
            System.out.println("Error: " + err0);
        else if(errNum == 1)
            System.out.println("Error: " + err1);
        else if(errNum == 2)
            System.out.println("Error: " + err2);
        else if(errNum == 3)
            System.out.println("Error: " + err3);
        else if(errNum == 4)
            System.out.println("Error: " + err4);
        else if(errNum == 5)
            System.out.println("Error: " + err5);
        else if(errNum == 6)
            System.out.println("Error: " + err6);
        else if(errNum == 7)
            System.out.println("Error: " + err7);
    }

    private byte[] ACKSend(short blockNum){ //check
        byte[] ack = new byte[4];
        ack[0] = (byte)0; 
        ack[1] = (byte)4;
        ack[2] = shortTobyte(blockNum)[0];
        ack[3] = shortTobyte(blockNum)[1];
        return ack;
    }

    private byte[] addDataToDirq(byte[] newData, byte[] dirqData){
        byte[] ans = new byte[dirqData.length + newData.length];
        for (int i = 0; i < dirqData.length; i++) {
            ans[i] = dirqData[i];
        }
        for (int i = 0; i < newData.length; i++) {
            ans[i + dirqData.length] = newData[i];
        }
        return ans;
    }   

    private byte[] shortTobyte(short a){
		return new byte []{(byte)(a >> 8) , (byte)(a & 0xff)};
	}
	
    private short byteToShort(byte[] byteArr, int fromIndex, int toIndex){
        return (short) ((((short)(byteArr[fromIndex]) & 0XFF)) << 8 | (short)(byteArr[toIndex] & 0XFF)); 
    }

    public String[] splitBySpace(String message){
        for(int i = 0; i < message.length(); i++){
            if(message.charAt(i) == ' '){
                String[] ans = new String[2];
                ans[0] = message.substring(0, i);
                ans[1] = message.substring(i+1);
                return ans;
            }
        }
        String[] ans = new String[1];
        ans[0] = message;
        return ans;
    }

}


