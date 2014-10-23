package org.ngsutils.support.progress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.ngsutils.NGSUtils;

public class SocketProgress extends BaseProgress {
    
    protected String socketPath=null;
    protected Thread serverThread = null;
    protected int port = 0;
    
    public SocketProgress() {
    }

    /**
     * 
     * @param socketPath - the socket port will be written to this file location
     */
    public SocketProgress(String socketPath) {
        this.socketPath = socketPath;
    }
    
    public SocketProgress(int port) {
        this.port = port;
    }
    
    @Override
    public void done() {
        super.done();
        if (socketPath != null) {
            new File(socketPath).delete();
        }
    }
    
    protected void startServer() {
        Runnable exec = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket server = new ServerSocket(port);
                    if (socketPath != null) {
                        FileOutputStream fos = new FileOutputStream(socketPath);
                        fos.write((""+server.getInetAddress().getCanonicalHostName()+":"+server.getLocalPort()+"\n").getBytes());
                        fos.close();
                    }
                    server.setSoTimeout(1000);
                    while (!done) {
                        try {
                            Socket client = server.accept();
                            client.getOutputStream().write(getStatusMessage().getBytes());
                            client.close();
                        } catch (SocketTimeoutException e) {
                            // no nothing...
                        }
                        
                    }
                    server.close();
                } catch (IOException e) {
                }
            }
        };
        
        serverThread = new Thread(exec);
        serverThread.start();
    }
    
    @Override
    public void start(long size) {
        super.start(size);
        if (serverThread ==null) {
            startServer();
        }
    }
    
    protected String getStatusMessage() {
        String str = "";
        
        str += "Command  : " + NGSUtils.getArgs() + "\n";
        if (name != null) {
            str += "Name     : " + name + "\n";
        }
        str += "\n";
        str += "Started  : " + startDate + "\n";
        str += "Elapsed  : " + secondsToString(elapsedMilliSec() / 1000) + "\n";
        str += "Remaining: " + secondsToString(estRemainingSec()) + "\n\n";

        str += "Total    : " + total + "\n";
        str += "Current  : " + current + " (" + String.format("%.1f", pctComplete()*100) + "%)\n";

        if (msg != null) {
            str += "\n";
            str += msg;
        }
        str += "\n";
        return str;
    }
}
