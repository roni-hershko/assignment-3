package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    
    private byte[] bytes = new byte[1 << 10]; 
    private int len = 0;
    private final int packetSize = 512;
    private int stopValue = packetSize;
    boolean thereIsZero = false;


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        Byte nextByteB = nextByte;

        if(nextByteB == 0){
            if (len == 0) 
                return null;
            if(thereIsZero){
                byte[]resultArray= resultArray(); 
                resetAllFields();
                return resultArray;  
            }
        }

        bytes[len] = nextByte;
        len++;
		Byte opcode = bytes[0];

		//case brodcast or error
        if((len == 2 && opcode == 9) || (len == 2 && opcode == 5)) 
        {
            thereIsZero = true;
        }

		//case ack
        else if(opcode == 4 ) 
        {
            stopValue = 3;  
        }
		//case data
        else if(opcode == 3 && len==3) 
        {
            stopValue = byteToShort(bytes, 1, 2) + 5;
        }

        if(len == stopValue){
            byte[]resultArray = resultArray(); 
            resetAllFields();
            return resultArray;
        }
        else
            return null;
    }

    @Override
    public byte[] encode(byte[] message) {
      return message;
    }

    public byte[] resultArray(){
		byte[] result = new byte[len];
		for(int i = 0; i < len; i++){
			result[i] = bytes[i];
		}
		return result;
	}

	public void resetAllFields(){
		bytes = new byte[1 << 10]; 
		len = 0;
		stopValue = packetSize;
		thereIsZero = false;
	}

    private short byteToShort(byte[] byteArr, int fromIndex, int toIndex){
        return (short) ((((short)(byteArr[fromIndex]) & 0XFF)) << 8 | (short)(byteArr[toIndex] & 0XFF)); 
    }

}