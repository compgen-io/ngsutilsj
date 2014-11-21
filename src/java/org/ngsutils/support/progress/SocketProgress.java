package org.ngsutils.support.progress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtils;

public class SocketProgress extends BaseProgress {
    protected static Thread serverThread = null;
    protected static List<SocketProgress> progresses = new ArrayList<SocketProgress>();
    

    protected String socketPath=null;
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
    synchronized
    public void done() {
        super.done();
        progresses.remove(this);
    }
    
    protected void startServer() {
        serverThread = new Thread(new Runnable() {
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
                    while (progresses.size() > 0) {
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
                } finally {
                    if (socketPath != null) {
                        new File(socketPath).delete();
                    }
                }
            }
        });
        
        //        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    @Override
    synchronized
    public void start(long size) {
        super.start(size);
        progresses.add(this);
        if (serverThread == null) {
            startServer();
        }
    }
    
    protected static String getStatusMessage() {
        String str = "";
        
        str += "Command  : " + NGSUtils.getArgs() + "\n";
        for (SocketProgress sp:progresses) {
            if (sp.name != null) {
                str += "Name     : " + sp.name + "\n";
            }
            str += "\n";
            str += "Started  : " + sp.startDate + "\n";
            str += "Elapsed  : " + secondsToString(sp.elapsedMilliSec() / 1000) + "\n";
            str += "Remaining: " + secondsToString(sp.estRemainingSec()) + "\n\n";
    
            str += "Total    : " + sp.total + "\n";
            str += "Current  : " + sp.current + " (" + String.format("%.1f", sp.pctComplete()*100) + "%)\n";
    
            if (sp.msg != null) {
                str += "\n";
                str += sp.msg;
            }
            str += "\n";
        
        }
        str += "\n";
        return str;
    }
}
