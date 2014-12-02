package org.ngsutils.junction;


public class JunctionCounts {
    private int[] counts;
    int[] donor_total = null;
    int[] acceptor_total = null;
    private double aveEditDistanceAcc = 0.0;
    int total = 0;
    
    public JunctionCounts(int sampleCount) {
        counts = new int[sampleCount];
        
        for (int i=0; i< sampleCount; i++) {
            counts[i] = 0;
        }
    }
    
    public void addCount(int index, int count) {
        addCount(index, count, 0.0);
    }

    public void addCount(int index, int count, double aveEditDistance) {
        this.counts[index] = count;
        this.total += count;
        if (aveEditDistance > 0.0) {
            aveEditDistanceAcc += (aveEditDistance * count);
        }
    }
    
    public double getAveEditDistance() {
        return aveEditDistanceAcc / total;
    }
    
    public int getTotal() {
        return total;
    }
    
    public int getCount(int index) {
        return counts[index];
    }

    public int getAcceptorTotal(int index) {
        if (!isValidAcceptor()) {
            return -1;
        }
        return acceptor_total[index];
    }

    public int getDonorTotal(int index) {
        if (!isValidDonor()) {
            return -1;
        }
        return donor_total[index];
    }

    public boolean isValidDonor() {
        return donor_total != null;
    }
    public boolean isValidAcceptor() {
        return acceptor_total != null;
    }

    public double getDonorPct(int index) {
        if (!isValidDonor()) {
            return -1;
        }
        if (donor_total[index] == 0) {
            return 0;
        }
        return ((double)counts[index])/donor_total[index];
    }

    public double getAcceptorPct(int index) {
        if (!isValidAcceptor()) {
            return -1;
        }
        if (acceptor_total[index] == 0) {
            return 0;
        }
        return ((double)counts[index])/acceptor_total[index];
    }
    
    public JunctionStats calcStats(Integer[] groups, boolean isDonor) {
        /*
         *  mean_pct = group_sum / group_donor_total
         *  var = mean_pct * (1 - mean_pct) / group_sum  (this is Binomial variance)
         *  psd = sqrt (group1_var + group2_var)
         *  tscore = (group1_mean_pct - group2_mean_pct) / psd
         */

        int group1_acc=0;
        int group1_common_acc=0;
        int group2_acc=0;
        int group2_common_acc=0;
        
        for (int i=0; i<groups.length; i++) {
            if (groups[i] == 1) {
                group1_acc += counts[i];
                if (isDonor) {
                    group1_common_acc += donor_total[i];
                } else {
                    group1_common_acc += acceptor_total[i];                        
                }
            } else if (groups[i] == 2) {
                group2_acc += counts[i];
                if (isDonor) {
                    group2_common_acc += donor_total[i];
                } else {
                    group2_common_acc += acceptor_total[i];                        
                }
            } else {
                throw new RuntimeException("Unsupported experimental design: group #"+groups[i]);
            }
        }
        
        return new JunctionStats(group1_acc, group1_common_acc, group2_acc, group2_common_acc);
   }
}
