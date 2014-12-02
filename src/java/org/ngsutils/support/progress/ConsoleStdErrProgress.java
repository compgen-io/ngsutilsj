package org.ngsutils.support.progress;

import java.io.PrintStream;

public class ConsoleStdErrProgress extends BaseProgress {
    public static final String[] spinner = new String[] {"|", "/", "-", "\\"};
    
    protected PrintStream out = System.err;
    protected int lastLineSize = 0;
    protected int spinnerPos = 0;
    
    protected long lastStatusPrint = 0;
    private boolean first = true;
    
    public void setOutput(PrintStream out) {
        this.out = out;
    }
    
    public void update(long pos, String msg) {
        if (first) {
            if (name!= null) {
                System.err.println(name);
            }
            first = false;
        }
        super.update(pos, msg);
        if (elapsedMilliSec() - lastStatusPrint > 1000) {
            writeStatus();
            lastStatusPrint = elapsedMilliSec();
        }
    }
    
    @Override
    public void done() {
        clearLine();
        out.println("Done! Elapsed: " + secondsToString(elapsedMilliSec() / 1000));
    }

    protected void writeStatus() {
        clearLine();

        String str = "";
        
        if (total > 0) {
            str += String.format("%.1f%% ", pctComplete()*100);
        }
        
        str += secondsToString(elapsedMilliSec() / 1000);
        str += " ";
        str += spinner[spinnerPos++];

        if (total > -1) {
            str += " [";
            int completed = (int) (pctComplete() * 20);
            for (int i=0; i<completed; i++) {
                str += "=";
            }
            str += ">";
            for (int i=0; i<(19-completed); i++) {
                str += " ";
            }
            str += "] ";
            str +=  secondsToString(estRemainingSec());
        }
        
        if (msg != null) {
            str += " ";
            str += msg;
        }
        
        if (spinnerPos >= spinner.length) {
            spinnerPos = 0;
        }
        
        lastLineSize = str.length();
        out.print("\r" + str);
    }
    
    protected void clearLine() {
        out.print("\r");
        for (int i=0; i< lastLineSize; i++) {
            out.print(" ");
        }
        out.print("\r");
    }
}
