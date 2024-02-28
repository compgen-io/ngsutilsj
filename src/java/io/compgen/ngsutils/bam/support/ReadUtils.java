package io.compgen.ngsutils.bam.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.vcf.VCFRecord;

public class ReadUtils {
    public static final int READ_PAIRED_FLAG = 0x1;
    public static final int PROPER_PAIR_FLAG = 0x2;
    public static final int READ_UNMAPPED_FLAG = 0x4;
    public static final int MATE_UNMAPPED_FLAG = 0x8;
    public static final int READ_STRAND_FLAG = 0x10;
    public static final int MATE_STRAND_FLAG = 0x20;
    public static final int FIRST_OF_PAIR_FLAG = 0x40;
    public static final int SECOND_OF_PAIR_FLAG = 0x80;
    public static final int NOT_PRIMARY_ALIGNMENT_FLAG = 0x100;
    public static final int READ_FAILS_VENDOR_QUALITY_CHECK_FLAG = 0x200;
    public static final int DUPLICATE_READ_FLAG = 0x400;
    public static final int SUPPLEMENTARY_ALIGNMENT_FLAG = 0x800;

    
    public static class MappedReadCounter {
        private Set<String> readsR1=new HashSet<String>();
        private Set<String> readsR2=null;
        
        private boolean separateReadCounts = false;
        private String tagName = null;
        
        private int tagAccR1=0;
        private int tagAccR2=0;
        private int tagCountR1=0;
        private int tagCountR2=0;
        
        public MappedReadCounter() {
        }

        public MappedReadCounter(boolean splitReads) {
            this(null, splitReads);
        }
        public MappedReadCounter(String tagName) {
            this(tagName, false);
        }

        public MappedReadCounter(String tagName, boolean separateReadCounts) {
            if (separateReadCounts) {
                readsR2=new HashSet<String>();
                this.separateReadCounts = separateReadCounts;
            }
            this.tagName = tagName.toUpperCase();
        }

        public void addRead(SAMRecord read) {
            if (!separateReadCounts || read.getFirstOfPairFlag() || !read.getReadPairedFlag()) {
                readsR1.add(read.getReadName());
                if (tagName != null) {
                    tagAccR1 += getTagValue(read);
                    tagCountR1 += 1;
                }
            } else if (separateReadCounts && !read.getFirstOfPairFlag() && read.getReadPairedFlag()) {
                readsR2.add(read.getReadName());
                if (tagName != null) {
                    tagAccR2 += getTagValue(read);
                    tagCountR2 += 1;
                }
            }
        }
        
        public int getTagValue(SAMRecord read) {
        	// Can't use the samtools version of this because it is case-sensitive
        	Integer val = null;
        	for (SAMTagAndValue tagval: read.getAttributes()) {
        		if (tagval.tag.toUpperCase().equals(tagName)) {
    			    val = coerceTagValueInt(tagval.tag, tagval.value);
    			    if (val != null) {
    			    	return val;
    			    } else {
    			    	return -1;
    			    }
        		}
        	}
        	return -1;
        }

        public int getCountR1() {
            return readsR1.size();
        }
        
        public int getCountR2() {
            return readsR2.size();
        }
        
        public double getTagMeanR1() {
            if (tagCountR1 > 0) {
                return (double) tagAccR1 / tagCountR1;
            }
            return 0;
        }
        
        public double getTagMeanR2() {
            if (tagCountR2 > 0) {
                return (double) tagAccR2 / tagCountR2;
            }
            return 0;
        }        
    }
    
    
    /**
     * Calculates the effective orientation for a given fragment. This is useful for strand-specific operations
     * where you want to filter out reads that aren't in the correct orientation.
     * 
     * @param read
     * @param orient - enum: RF, FR, or unstranded
     * @return enum Strand: PLUS, MINUS (null for unmapped)
     */
    public static Strand getFragmentEffectiveStrand(SAMRecord read, Orientation orient) {
        if (read.getReadUnmappedFlag()) {
            return null;
        }
        
        // if we don't know the orientation, treat it as-is.
        if (orient == null || orient == Orientation.UNSTRANDED) {
            if (read.getReadNegativeStrandFlag()) {
                return Strand.MINUS;
            } else {
                return Strand.PLUS;
            }
        }
        
        if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
            // unpaired or first read in a pair
            if (orient == Orientation.FR) {
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            }
        } else {
            // paired end and second read...
            if (orient == Orientation.FR) {
                // this assumes read1 and read2 are sequenced in opposite
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            }
        }
    }
    
    /**
     * For a given read, calculate the splice-junctions it crosses. A read may cross more than one junction.
     * This is determined by looking for an 'N' operation in the alignment string. If a minimum overlap is 
     * specified, then the read must have more than minOverlap bases on each side of the junction. If there is more
     * than one junction, then only the first and last flanking sequences need to be longer than minOverlap.
     * 
     * If there is no junction, then this function will return the entire read region.
     * 
     * @param read 
     * @param orient - the orientation for the sequencing library (FR, RF, etc).
     * @param minOverlap - the minimum amount of flanking sequence there needs to be (default: 4bp).
     * @return
     */
    public static List<GenomeSpan> getJunctionFlankingRegions(SAMRecord read, Orientation orient, int minOverlap) {
        List<GenomeSpan> out = new ArrayList<GenomeSpan>();
        Strand strand = getFragmentEffectiveStrand(read, orient);
        
        int refpos = read.getAlignmentStart() - 1; // alignment-start is 1-based
//        int readpos = 0;
        
        int flankStart = refpos;
        
        for (CigarElement el: read.getCigar().getCigarElements()) {
            switch (el.getOperator()) {
            case M:
            case EQ:
            case X:
                refpos += el.getLength();
//                readpos += el.getLength();
                break;
            case D: // NOTE: changed this 2020-12-11, was case I, but that was wrong...
                refpos += el.getLength();
                break;
//            case S:
//                readpos += el.getLength();
//                break;
            case N:
          	    out.add(new GenomeSpan(read.getReferenceName(), flankStart, refpos, strand));
                refpos += el.getLength();
                flankStart = refpos;
                break;
            case H:
            default:
                break;
                
            }
        }
        
        out.add(new GenomeSpan(read.getReferenceName(), flankStart, refpos, strand));

        // check the first and last flanks... if they are too short, then don't return anything.
        if (out.size() > 1) {
            GenomeSpan first = out.get(0);
            GenomeSpan last = out.get(out.size()-1);
        	
            if (first.length() < minOverlap) {
                out.clear();
            } else if (last.length() < minOverlap) {
                out.clear();
            }
        }
        
        return out;
    }
    public static List<GenomeSpan> getJunctionFlankingRegions(SAMRecord read, Orientation orient) {
        return getJunctionFlankingRegions(read, orient, 4);
    }

    
    public static int getSamReadLength(SamReader reader) {
    	return getSamReadLength(reader, 10000);
    }
    public static int getSamReadLength(SamReader reader, int recordsToScan) {
    	int i = 0;
    	int size = 0;
        SAMRecordIterator it = reader.iterator();
        while (it.hasNext() && i < recordsToScan) {
        	SAMRecord read = it.next();
        	size = Math.max(size,  read.getReadLength());
        	i++;
        }
        it.close();
        return size;
    }

    /** 
     * Find reads that overlap a given genomic region
     * @param reader
     * @param pos
     * @param orient
     * @param readLength
     * @param minOverlap
     * @return
     */
    public static List<SAMRecord> findOverlappingReads(SamReader reader, GenomeSpan pos, Orientation orient, int readLength, int minOverlap) {
        return findOverlappingReads(reader, pos, orient, readLength, minOverlap, false);
    }

    /** 
     * Find reads that overlap a given genomic region
     * @param reader
     * @param pos
     * @param orient
     * @param readLength
     * @param minOverlap
     * @param allowGaps
     * @return
     */
    public static List<SAMRecord> findOverlappingReads(SamReader reader, GenomeSpan pos, Orientation orient, int readLength, int minOverlap, boolean allowGaps) {
    	List<SAMRecord> out = new ArrayList<SAMRecord>();

    	SAMRecordIterator it = reader.query(pos.ref, pos.start - readLength + minOverlap, pos.start + readLength - minOverlap, true);
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
            
            if (!allowGaps) {
                // skip all reads with gaps
                for (CigarElement el: read.getCigar().getCigarElements()) {
                    if (el.getOperator() == CigarOperator.N) {
                        continue;
                    }
                }
            }
            
            if (ReadUtils.getFragmentEffectiveStrand(read, orient) != pos.strand) {
            	continue;
            }
            
            for (GenomeSpan region: ReadUtils.getJunctionFlankingRegions(read, orient, minOverlap)) {
            	if (region.start <= (pos.start - minOverlap) && region.end >= (pos.start + minOverlap)) {
            		out.add(read);
            		break;
            	}
            }
        }
        it.close();
    	return out;
    }

    /**
     * Given a SamReader, find all reads within a region (ref:start-end) that span a splice junction and tally the number of times
     * each junction is spanned. Optionally tally the average values of "tallyTagName" tags (eg. NM).
     * 
     * @param reader
     * @param ref
     * @param start
     * @param end
     * @param orient
     * @param minOverlap
     * @param tallyTagName
     * @param separateReadCounts
     * @return
     */
    public static SortedMap<GenomeSpan, MappedReadCounter> countJunctions(SamReader reader, String ref, int start, int end, Orientation orient, int minOverlap, String tallyTagName) {
        SAMRecordIterator it = reader.query(ref, start, end, true);
        SortedMap<GenomeSpan, MappedReadCounter> counters = new TreeMap<GenomeSpan, MappedReadCounter>();
        
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
           
            if (isJunctionSpanning(read)) {
                for (GenomeSpan junction: getJunctionsForRead(read, orient, minOverlap)) {
                    if (!counters.containsKey(junction)) {
                        counters.put(junction, new MappedReadCounter(tallyTagName));
                    }
                    
                    counters.get(junction).addRead(read);
                }
            }
        }
        it.close();
        return counters;
    }
    
    public static List<GenomeSpan> getJunctionsForRead(SAMRecord read, Orientation orient) {
        return getJunctionsForRead(read, orient, 4);
    }
    
    /**
     * Return a GenomeSpan describing the splice junctions found for a particular read.
     * 
     * Example:
     *
     * ----[][][][][][][]-------------------[][][]---------------[][][][][][][]-----
     *             XXXXXX-------------------XXXXXX---------------XXXX
     * 
     * Will return:
     *                   *******************      ***************
     * 
     * @param read
     * @param orient - The library orientation (RF, FR, Unstranded)
     * @param minOverlap - junctions must have minOverlap bases on either side of the junction (used in getFlankingRegions)
     * @return
     */
    public static List<GenomeSpan> getJunctionsForRead(SAMRecord read, Orientation orient, int minOverlap) {
        List<GenomeSpan> junctions = new ArrayList<GenomeSpan>();
        int last_end = -1;
        for (GenomeSpan flank: ReadUtils.getJunctionFlankingRegions(read, orient, minOverlap)) {
            if (last_end != -1) {
                GenomeSpan junction = new GenomeSpan(read.getReferenceName(), last_end, flank.start, flank.strand);
                junctions.add(junction);
            }
            last_end = flank.end;
        }
        return junctions;
    }
     
    private static Integer coerceTagValueInt(String tag, Object val) {
    	// from HTSJDK: https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/SAMRecord.java
    	
        if (val == null) return null;
        if (val instanceof Integer) {
            return (Integer)val;
        }
        if (!(val instanceof Number)) {
            throw new RuntimeException("Value for tag " + tag + " is not Number: " + val.getClass());
        }
        final long longVal = ((Number)val).longValue();
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
            throw new RuntimeException("Value for tag " + tag + " is not in Integer range: " + longVal);
        }
        return (int)longVal;

    }

    public static boolean isReadUniquelyMapped(SAMRecord read) {
        Integer mappings = read.getIntegerAttribute("NH");
        if (mappings != null) {
            return (mappings == 1);
        }
        mappings = read.getIntegerAttribute("IH");
        if (mappings != null) {
            return (mappings == 1);
        }
        return read.getMappingQuality() != 0;
    }

    public static boolean isJunctionSpanning(SAMRecord read) {
        for (CigarElement el: read.getCigar().getCigarElements()) {
            if (el.getOperator() == CigarOperator.N) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOrphan(SAMRecord read) {
    	return (read.getReadPairedFlag() && (read.getMateUnmappedFlag()));
    }
    
    public static boolean isDiscordant(SAMRecord read, int maxDist) {
        return isDiscordant(read, maxDist, true);
    }
    public static boolean isDiscordant(SAMRecord read, int maxDist, boolean includeIntraChrom) {
        if (read.getReferenceIndex() != read.getMateReferenceIndex()) {
            return true;
        }
        
        if (read.getReadNegativeStrandFlag() && read.getMateNegativeStrandFlag()) {
            return true;
        }
        
        if (!read.getReadNegativeStrandFlag() && !read.getMateNegativeStrandFlag()) {
            return true;
        }
        
        if (includeIntraChrom && Math.abs(read.getAlignmentEnd() - read.getMateAlignmentStart()) > maxDist) {
            return true;
        }
        
        return false;
    }
    
    public static SAMRecord removeClipping(SAMRecord read) throws InvalidReadException {
        return removeClipping(read, false);
    }
    public static SAMRecord removeClipping(SAMRecord read, boolean writeFlags) throws InvalidReadException {
        int clip5 = 0;
        int clip3 = 0;
        float origLength = read.getReadBases().length;
        
        boolean inseq = false;
        boolean changed = false;
        
        Cigar newCigar = new Cigar();
        
        for (CigarElement ce: read.getCigar().getCigarElements()) {
            if (ce.getOperator() == CigarOperator.H) {
                // skip this element
                changed = true;
            } else if (ce.getOperator() == CigarOperator.S) {
                changed = true;
                if (!inseq) {
                    clip5 = ce.getLength();
                } else {
                    clip3 = ce.getLength();
                }
            } else {
                inseq = true;
                newCigar.add(ce);
            }
        }

        if (!changed) {
            return read;
        }
        
        if (read.getReadBases().length != read.getBaseQualities().length) {
            throw new InvalidReadException("Sequence is not the same length as the base qualities! Did you try to remove clipped bases from a color-space read? (Not supported)");
        }
        
        byte[] newSeq = new byte[read.getReadBases().length - clip3 - clip5];
        byte[] newQual = new byte[read.getBaseQualities().length - clip3 - clip5];

        int origpos = clip5;
        int newpos = 0;

        int newlen = read.getReadBases().length - clip5 - clip3;

        while (newpos < newlen) {
            newSeq[newpos] = read.getReadBases()[origpos];
            newQual[newpos] = read.getBaseQualities()[origpos];
            
            newpos++;
            origpos++;
        }

        read.setReadBases(newSeq);
        read.setBaseQualities(newQual);
        read.setCigar(newCigar);

        if (writeFlags) {
            read.setAttribute("ZA", clip5);
            read.setAttribute("ZB", clip3);
            read.setAttribute("ZC", (clip5+clip3) / origLength);
        }
        
        return read;
    }

	public static SAMRecord findMate(SamReader reader, SAMRecord read) {
		return findMate(reader, read, 0, 0);
	}
    
	public static SAMRecord findMate(SamReader reader, SAMRecord read, int filterFlags, int requiredFlags) {
        if (!read.getReadPairedFlag()) {
            throw new IllegalArgumentException("findMate requires paired end reads!");
        }
        SAMRecord mate = null;
        SAMRecordIterator it = null;
        
        if (read.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            it = reader.queryUnmapped();
        } else {
            it = reader.query(read.getMateReferenceName(), read.getMateAlignmentStart(), read.getMateAlignmentStart(), false);
        }
        
        while (it.hasNext() && mate == null) {
            SAMRecord q = it.next();
            if (filterFlags > 0) {
            	if ((q.getFlags() & filterFlags) != 0) {
            		continue;
            	}
            }

            if (requiredFlags > 0) {
            	if ((q.getFlags() & requiredFlags) != requiredFlags) {
            		continue;
            	}
            }
            
            if (q.getReadName().equals(read.getReadName())) {
            	if (read.getFirstOfPairFlag()) {
            		if (q.getFirstOfPairFlag()) {
            			continue; 
            		}
            	} else {
            		if (!q.getFirstOfPairFlag()) {
            			continue; 
            		}
            	}
            	
                boolean found = true; 
            	for (SAMTagAndValue tv: read.getAttributes()) {
            		// if the read has an IH tag -- require the mate to have the same IH tag
            		// if you have supplementary mappings, they can have the same location information, 
            		// so this is the indicator of *which* alignment to use.
            		if (tv.tag.equals("IH")) {
            			found = false;
                    	for (SAMTagAndValue tv2: q.getAttributes()) {
                    		if (tv2.tag.equals("IH")) {
                    			if (tv.value.equals(tv2.value)) {
                    				found = true;
                    				break;
                    			}
                    		}
                    	}
                    	if (found) {
                    		break;
                    	}
            		}
            	}
            	if (found) {
                // this won't happen because we break out of the while loop first
//            		if (mate != null) {
//                        throw new IllegalArgumentException("findMate found multiple records for read: "+read.getReadName());
//            		}
            		mate = q;
            	}
            }
		}
        it.close();
		return mate;
	}
  
  // Does a read contain a particular variant?
	public static boolean containsVariant(SAMRecord read, VCFRecord rec, String allele, IndexedFastaFile fasta) throws IOException {
		if (!read.getReferenceName().equals(rec.getChrom())) {
			return false;
		}
		
		int refpos = read.getAlignmentStart() - 1; // HTSJDK is 1-based
		int readpos = 0;
		int recpos = rec.getPos() - 1; // VCFs are 1-based

		boolean isInsert = false;
		boolean isDel = false;
		
		if (rec.getRef().length() > 1) {
			// AAAC > A
			isDel = true; 
		} else if (allele.length()>1) {
			isInsert = true;
		}
		
		
		
//		boolean indel = isInsert | isDel;
		String flankBase = "";
		
		
//		if (indel && read.getReadName().equals("MG01HX10:127:H7FFGCCXY:7:1102:31416:39387")) {
//		System.err.println("Read : " + read.getReadName());
//		System.err.println("pos  : " + refpos);
//		System.err.println("seq  : " + read.getReadString());
//		System.err.println("CIGAR: " + read.getCigarString());
//		}
		for (CigarElement e: read.getCigar().getCigarElements()) {
			if (e.getOperator() == CigarOperator.M || e.getOperator() == CigarOperator.X || e.getOperator() == CigarOperator.EQ) {
				if (refpos + e.getLength() < recpos) {
					// variant is outside this span
//					if (indel && read.getReadName().equals("MG01HX10:127:H7FFGCCXY:7:1102:31416:39387")) {
//					System.err.println("Skipping "+e.getLength()+" bases");
//					}
					readpos += e.getLength();
					refpos += e.getLength();
				} else {
//
					// variant is in this span
//					if (indel && read.getReadName().equals("MG01HX10:127:H7FFGCCXY:7:1102:31416:39387")) {
//						for (int i=0; i< e.getLength(); i++) {
//							if (refpos + i == recpos) {
//								System.err.println((refpos+i)+":"+read.getReadString().charAt(readpos+i) +" *");
//							} else {
//								System.err.println((refpos+i)+":"+read.getReadString().charAt(readpos+i));    						
//							}
//							
//							if (refpos + i == recpos + allele.length()-1) {
//								break;
//							}
//						}
//					}

//					System.err.println("");
//					System.err.println("recpos: "+ recpos);
//					System.err.println("refpos: "+ refpos);
//					System.err.println("readpos: "+ readpos);
//					System.err.println("offset: "+ offset);
//					System.err.println("offset + readpos: "+ (offset + readpos));
//					System.err.println("recpos - refpos + readpos: "+ (recpos - refpos + readpos));
										
					
//					System.err.println("");
//					System.err.println((refpos + recpos)+":"+(recpos - refpos + readpos)+":"+base);
					
					int offset = recpos - refpos + readpos;
					
					if (offset + allele.length() < read.getReadString().length()) {
						if (isDel) {
							String base = "" + read.getReadString().substring(offset, offset+allele.length());
							if (allele.length() == base.length()) {
								// this is the ref side of an del...
//								System.err.println(allele + " =? " + base);
								return allele.equals(base);
							}
							flankBase = base;
						} else if (isInsert) {
							// indels include a flanking base for the ref or alt, so we need to push through.
							String base = "" + read.getReadString().substring(offset, offset+rec.getRef().length());
							if (allele.length() == base.length()) {
								// this is the ref side of an insert...
//								System.err.println(allele + " =? " + base);
								return allele.equals(base);
							}
//							flankBase = base;
						} else {
							// if we aren't an indel, so just match directly
							String base = "" + read.getReadString().substring(offset, offset+allele.length());
							return allele.equals(base);
						}

						// we fell through an indel...
						readpos += e.getLength();
						refpos += e.getLength();
						
					} else {
						return false;
					}
					
				}
			} else if (e.getOperator() == CigarOperator.I) {
//				System.err.println("insertion: " + readpos + " + "+ e.getLength());
				if (refpos == recpos + rec.getRef().length()) {
					String insert = read.getReadString().substring(readpos - rec.getRef().length(), readpos + allele.length() - rec.getRef().length());
//					System.err.println("insert: " + insert);
					return insert.equals(allele);
				}

				readpos += e.getLength();
			} else if (e.getOperator() == CigarOperator.D) {
				if (refpos == recpos + allele.length()) {
					String del = fasta.fetchSequence(rec.getChrom(), refpos, refpos + rec.getRef().length() - allele.length());
//					System.err.println("flank/del: " + flankBase + "/" + del);
					return (flankBase + del).equals(rec.getRef()) && flankBase.equals(allele);
				}
				
				refpos += e.getLength();
			} else if (e.getOperator() == CigarOperator.N) {
				// long gap
				refpos += e.getLength();
			} else if (e.getOperator() == CigarOperator.S) {
				// soft clip -- skip these bases in read
				readpos += e.getLength();
			} else if (e.getOperator() == CigarOperator.H) {
				// hard clip -- ignore
			} else {
				throw new IOException("Unsupported CIGAR element: " + e.getOperator()+ " / "+ read.getCigarString());
			}
		}
		
		return false;
	}
}
