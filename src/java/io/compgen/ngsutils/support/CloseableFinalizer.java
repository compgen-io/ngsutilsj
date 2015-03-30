package io.compgen.ngsutils.support;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.common.progress.ProgressFinalizer;

import java.util.Iterator;

public class CloseableFinalizer<T> implements ProgressFinalizer<T> {
    @Override
    public void finalize(Iterator<T> it) {
        if (it instanceof CloseableIterator) {
            ((CloseableIterator<T>) it).close();
        }
    }
}
