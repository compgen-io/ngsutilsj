package io.compgen.ngsutils.support;

import java.util.Iterator;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.common.progress.ProgressFinalizer;

public class CloseableFinalizer<T> implements ProgressFinalizer<T> {
    @Override
    public void finalize(Iterator<T> it) {
        if (it instanceof CloseableIterator) {
            ((CloseableIterator<T>) it).close();
        }
    }
}
