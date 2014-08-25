package org.ngsutils.junction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.stats.PermutedNullDistribution;

public class JunctionDiff {
    
    private int minTotalCount = -1;
    private double maxEditDistance = -1;
    private Boolean splitReads = null;
    private List<String> sampleNames = new ArrayList<String>();
    private int sampleCount = -1;
    
    private SortedMap<JunctionKey, JunctionCounts> junctions = new TreeMap<JunctionKey, JunctionCounts> ();
    private SortedMap<JunctionDonorAcceptor, List<JunctionKey>> donors = new TreeMap<JunctionDonorAcceptor, List<JunctionKey>> ();
    private SortedMap<JunctionDonorAcceptor, List<JunctionKey>> acceptors = new TreeMap<JunctionDonorAcceptor, List<JunctionKey>> ();

    private PermutedNullDistribution permutedDonorR1 = null;
    private PermutedNullDistribution permutedDonorR2 = null;
    private PermutedNullDistribution permutedAcceptorR1 = null;
    private PermutedNullDistribution permutedAcceptorR2 = null;

    private int permutedGroupCount = 0;
    
    public JunctionDiff() {}
    
    public void setMinTotalCount(int minTotalCount) {
        this.minTotalCount  = minTotalCount;
    }
    public void setMaxEditDistance(double maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
    }
    
    public JunctionDiffStats findJunctions(List<String> filenames, Integer[] groups) throws IOException, NGSUtilsException {
        sampleCount = filenames.size();
        System.err.println("Number of samples: "+ sampleCount);
        sampleNames = StringUtils.getUniqueNames(filenames);
        
        JunctionDiffStats stats = new JunctionDiffStats();
        
        for (int i=0; i< sampleCount; i++) {
            stats.addSample(filenames.get(i), sampleNames.get(i), groups[i]);
            readFile(filenames.get(i), sampleCount, i);
        }
        
        stats.setTotalJunctions(junctions.size());
        
        
        if (minTotalCount > -1 || maxEditDistance > -1) {
            filterJunctions();
            stats.setFilteredJunctions(junctions.size());
        }
        
        populateDonorAcceptors();
        
        List<JunctionDonorAcceptor> validDonors = calculateDonors();
        stats.setValidDonors(validDonors.size());
        List<JunctionDonorAcceptor> validAcceptors = calculateAcceptors();
        stats.setValidAcceptors(validAcceptors.size());

        filterValidDonorAcceptorJunctions(validDonors, validAcceptors);
        stats.setDonorAcceptorFilteredJunctions(junctions.size());

        calcPermutations(groups);
        return stats;
    }

    private void filterValidDonorAcceptorJunctions(List<JunctionDonorAcceptor> validDonors, List<JunctionDonorAcceptor> validAcceptors) {
        Set<JunctionKey> validJunctions = new HashSet<JunctionKey>();
        for (JunctionDonorAcceptor d:validDonors) {
            validJunctions.addAll(donors.get(d));
        }
        for (JunctionDonorAcceptor a:validAcceptors) {
            validJunctions.addAll(acceptors.get(a));
        }
        List<JunctionKey> removeme = new ArrayList<JunctionKey>();

        for (JunctionKey k: junctions.keySet()) {
            if (!validJunctions.contains(k)) {
                removeme.add(k);
            }
        }
        for (JunctionKey k: removeme) {
            junctions.remove(k);
        }        

    }

    private void populateDonorAcceptors() {
        for (JunctionKey k: junctions.keySet()) {
            if (!donors.containsKey(k.donor)) {
                donors.put(k.donor, new ArrayList<JunctionKey>());                            
            }
            if (!acceptors.containsKey(k.acceptor)) {
                acceptors.put(k.acceptor, new ArrayList<JunctionKey>());                            
            }
            donors.get(k.donor).add(k);
            acceptors.get(k.acceptor).add(k);
        }        
    }

    private void filterJunctions() {
        List<JunctionKey> removeme = new ArrayList<JunctionKey>();
        
        for (JunctionKey k: junctions.keySet()) {
            if (minTotalCount > -1) {
                if (junctions.get(k).total < minTotalCount) {
                    removeme.add(k);
                    continue;
                }
            }
            if (maxEditDistance > -1) {
                if (junctions.get(k).getAveEditDistance() > maxEditDistance) {
                    removeme.add(k);
                }
            }
        }
        
        for (JunctionKey k: removeme) {
            junctions.remove(k);
        }        
    }

    private List<JunctionDonorAcceptor> calculateDonors() {
        List<JunctionDonorAcceptor> valid = new ArrayList<JunctionDonorAcceptor>();
        for (JunctionDonorAcceptor donor: donors.keySet()) {
            if (donors.get(donor).size() > 1) {
                valid.add(donor);
                int totals[] = new int[sampleCount];
                for (int i=0; i<donors.get(donor).size(); i++) {
                    for (int j=0; j<sampleCount; j++) {
                        totals[j] += junctions.get(donors.get(donor).get(i)).getCount(j);
                    }
                }
                for (int i=0; i<donors.get(donor).size(); i++) {
                    junctions.get(donors.get(donor).get(i)).donor_total = totals;
                }
            }
        }
        return valid;
    }

    private List<JunctionDonorAcceptor> calculateAcceptors() {
        List<JunctionDonorAcceptor> valid = new ArrayList<JunctionDonorAcceptor>();
        for (JunctionDonorAcceptor acceptor: acceptors.keySet()) {
            if (acceptors.get(acceptor).size() > 1) {
                valid.add(acceptor);
                int totals[] = new int[sampleCount];
                for (int i=0; i<acceptors.get(acceptor).size(); i++) {
                    for (int j=0; j<sampleCount; j++) {
                        totals[j] += junctions.get(acceptors.get(acceptor).get(i)).getCount(j);
                    }
                }
                for (int i=0; i<acceptors.get(acceptor).size(); i++) {
                    junctions.get(acceptors.get(acceptor).get(i)).acceptor_total = totals;
                }                
            }
        }
        return valid;
    }

    private void readFile(String filename, int sampleCount, int sampleNum) throws IOException, NGSUtilsException {
        String[] header = null;
        
        int juncIdx = -1;
        int strandIdx = -1;
        int readIdx = -1;
        int countIdx = -1;
        int editIdx = -1;
        
        StringLineReader reader = new StringLineReader(filename);
        
        boolean fileSplitReads = false;
        for (String line: reader) {
            if (line != null && line.charAt(0) != '#') {
                String[] cols = StringUtils.strip(line).split("\t");
                if (header == null) {
                    // process header, look for column names, and assign column-indexes
                    header = cols;
                    
                    for (int i=0; i< header.length; i++) {
                        switch(header[i]) {
                        case "junction":
                            juncIdx = i;
                            break;
                        case "strand":
                            strandIdx = i;
                            break;
                        case "readnum":
                            fileSplitReads = true;
                            readIdx = i;
                            break;
                        case "count":
                            countIdx = i;
                            break;
                        case "avg-edit-distance":
                            editIdx = i;
                            break;
                        default:
                            break;
                        }
                    }
                    if (splitReads == null) {
                        splitReads = fileSplitReads;
                    } else {
                        if (splitReads != fileSplitReads) {
                            throw new NGSUtilsException("You can not compare split-read samples and non-split-read samples!");
                        }
                    }
                } else {
                    // this is a junction line... find the key, if it is new, add a count object, 
                    // and add the counts for this sample.

                    boolean read1 = true;
                    
                    if (readIdx > -1) {
                        read1 = !cols[readIdx].equals("R2"); 
                    }
                    
                    JunctionKey k = new JunctionKey(cols[juncIdx], Strand.parse(cols[strandIdx]), read1);

                    if (!junctions.containsKey(k)) {
                        junctions.put(k, new JunctionCounts(sampleCount));
                    }

                    if (editIdx > -1) {
                        junctions.get(k).addCount(sampleNum, Integer.parseInt(cols[countIdx]), Double.parseDouble(cols[editIdx]));
                    } else {
                        junctions.get(k).addCount(sampleNum, Integer.parseInt(cols[countIdx]));
                    }
                }
            }
        }       
        reader.close();
    }

    public boolean isSplitReads() {
        return splitReads;
    }

    public List<String> getSampleNames() {
        return Collections.unmodifiableList(sampleNames);
    }

    public SortedMap<JunctionKey, JunctionCounts> getJunctions() {
        return Collections.unmodifiableSortedMap(junctions);
    }

    /**
     * Calculates t-score distributions for all junctions by readnum and donor/acceptor
     * These permuted t-scores will then be used to calculate FDRs for the true t-scores
     * @param groups
     */
    private void calcPermutations(Integer[] trueGroups) {
        List<Integer[]> permutedGroups = permuteGroups(trueGroups);
        this.permutedGroupCount = permutedGroups.size();
        
        this.permutedDonorR1 = calcPermutations(permutedGroups, true, true);
        this.permutedAcceptorR1 = calcPermutations(permutedGroups, true, false);

        if (this.isSplitReads()) {
            this.permutedDonorR2 = calcPermutations(permutedGroups, false, true);
            this.permutedAcceptorR2 = calcPermutations(permutedGroups, false, false);
        }

        List<Integer[]> trueGroupList = new ArrayList<Integer[]>();
        trueGroupList.add(trueGroups);

//        this.trueDonorR1 = calcPermutations(trueGroupList, true, true);
//        this.trueAcceptorR1 = calcPermutations(trueGroupList, true, false);
//
//        if (this.isSplitReads()) {
//            this.trueDonorR2 = calcPermutations(trueGroupList, false, true);
//            this.trueAcceptorR2 = calcPermutations(trueGroupList, false, false);
//        }
        
    }
    
    
    public double calcPvalue(double testScore, boolean isRead1, boolean isDonor) {
        PermutedNullDistribution nullDist;

        if (isRead1) {
            if (isDonor) {
                nullDist = permutedDonorR1;
            } else {
                nullDist = permutedAcceptorR1;
            }
        } else {
            if (isDonor) {
                nullDist = permutedDonorR2;
            } else {
                nullDist = permutedAcceptorR2;
            }
        }
        
        return nullDist.pvalue(testScore);
//        
//        testScore = Math.abs(testScore);
//        
//        int i;
//        for (i=0; i<permutedScores.length && testScore <= permutedScores[i]; i++) {}
//
//        return (double) (i + 1) / permutedScores.length;
    }
    private PermutedNullDistribution calcPermutations(List<Integer[]> permutedGroups, boolean isRead1, boolean isDonor) {
        PermutedNullDistribution nullDist = new PermutedNullDistribution();
        nullDist.setName((isDonor ? "Donor": "Acceptor")+"-"+(isRead1 ? "R1": "R2"));
        
        for (JunctionKey junc: junctions.keySet()) {
            if ((isDonor && junctions.get(junc).isValidDonor()) || (!isDonor && junctions.get(junc).isValidAcceptor())) {
                if (junc.read1 == isRead1) {
                    for (Integer[] group: permutedGroups) {
                        JunctionStats stats = junctions.get(junc).calcStats(group, isDonor);
                        nullDist.add(stats.tScore);
                    }
                }
            }
        }

        return nullDist;        
    }

    public static List<Integer[]> permuteGroups(Integer[] trueGroups) {
        int group1Count = 0;
        for (int g:trueGroups) {
            if (g == 1) {
                group1Count += 1;
            }
        }
        
        Integer[] possible = new Integer[trueGroups.length];
        for (int i=0; i<trueGroups.length; i++) {
            possible[i] = i;
        }
        List<Integer[]> permutedGroup1 = new ArrayList<Integer[]>();
        calcRecursePermutations(permutedGroup1, new Integer[0], 0, trueGroups.length, group1Count);
        
        List<Integer[]> permuted = new ArrayList<Integer[]>();
        
        for (Integer[] group1: permutedGroup1) {
            Integer[] permutedGroup = new Integer[trueGroups.length];
            for (int i=0; i< trueGroups.length; i++) {
                permutedGroup[i] = 2;
            }
            for (int i=0; i< group1.length; i++) {
                permutedGroup[group1[i]] = 1;
            }
            // Don't include the "true" grouping in list.
            if (!Arrays.equals(permutedGroup, trueGroups)) {
                permuted.add(permutedGroup);
            }
        }
        
        return permuted;
        
    }

    private static void calcRecursePermutations(List<Integer[]> valid, Integer[] curr, int start, int samples, int maxLength) {
        if (curr.length>=maxLength) {
            valid.add(curr);
            return;
        }
        
        for (int i=start; i<samples; i++) {
            Integer[] copy = Arrays.copyOf(curr, curr.length+1);
            copy[curr.length] = i;
            calcRecursePermutations(valid, copy, i+1, samples, maxLength);
        }        
    }

    public Integer getPermutedGroupCount() {
        return this.permutedGroupCount;
    }
}
