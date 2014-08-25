package org.ngsutils.junction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JunctionDiffStats {
    public class JunctionDiffSample {
        public final String filename;
        public final String sampleName;
        public final int group;
        public JunctionDiffSample(String filename, String sampleName, int group) {
            this.filename = filename;
            this.sampleName = sampleName;
            this.group = group;
        }
    }

    private List<JunctionDiffSample> samples = new ArrayList<JunctionDiffSample>();
    private int totalJunctions = -1;
    private int filteredJunctions = -1;
    private int donorAcceptorFilteredJunctions = -1;
    private int validDonors = -1;
    private int validAcceptors = -1;

    public void addSample(String filename, String sampleName, int group) {
        samples.add(new JunctionDiffSample(filename, sampleName, group));
    }

    public int getTotalJunctions() {
        return totalJunctions;
    }

    public void setTotalJunctions(int totalJunctions) {
        this.totalJunctions = totalJunctions;
    }

    public int getFilteredJunctions() {
        return filteredJunctions;
    }

    public void setFilteredJunctions(int filteredJunctions) {
        this.filteredJunctions = filteredJunctions;
    }

    public int getDonorAcceptorFilteredJunctions() {
        return donorAcceptorFilteredJunctions;
    }

    public void setDonorAcceptorFilteredJunctions(int donorAcceptorFilteredJunctions) {
        this.donorAcceptorFilteredJunctions = donorAcceptorFilteredJunctions;
    }

    public int getValidDonors() {
        return validDonors;
    }

    public void setValidDonors(int validDonors) {
        this.validDonors = validDonors;
    }

    public int getValidAcceptors() {
        return validAcceptors;
    }

    public void setValidAcceptors(int validAcceptors) {
        this.validAcceptors = validAcceptors;
    }

    public List<JunctionDiffSample> getSamples() {
        return Collections.unmodifiableList(samples);
    }

}
