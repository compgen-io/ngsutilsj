package io.compgen.ngsutils.support.progress;

import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileChannelStats implements ProgressStats {
    private FileChannel channel = null;
    public FileChannelStats(FileChannel channel) {
        this.channel = channel;
    }
    @Override
    public long size() {
        if (channel != null) {
            try {
                return channel.size();
            } catch (IOException e) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public long position() {
        if (channel != null) {
            try {
                return channel.position();
            } catch (IOException e) {
                return -1;
            }
        }
        return -1;
    }
}
