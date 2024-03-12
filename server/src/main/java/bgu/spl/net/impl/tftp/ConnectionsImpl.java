package bgu.spl.net.impl.tftp;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionHandler;
import java.util.concurrent.ConcurrentHashMap;


public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionsMap;


    public ConnectionsImpl(){
        connectionsMap = new ConcurrentHashMap<>();
    }
    

    public boolean connect(int connectionId, ConnectionHandler<T> handler){
        if(connectionsMap.containsKey(connectionId))
            return false;
        else{
            connectionsMap.put(connectionId, handler);
            return true;
        }
    }


    public boolean send(int connectionId, T msg){
        connectionsMap.get(connectionId).send(msg);
        return true;
    }


    public void disconnect(int connectionId){
        if(connectionsMap.containsKey(connectionId))
            connectionsMap.remove(connectionId);
    }



	//added
	public void brodcast(T msg){
		for (Map.Entry<Integer, ConnectionHandler<T>> entry : connectionsMap.entrySet()) {
			entry.getValue().send(msg);
		}
	}
}


   