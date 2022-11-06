package io.compgen.ngsutils.support;

import java.util.Arrays;
import java.util.Comparator;

import io.compgen.common.StringUtils;

public class Sorter {

	public static void sortValues(String[][] vals) throws Exception{
		sortValues(vals, StringUtils.repeat("s", vals.length));
	}

	/**
	 * 
	 * @param vals
	 * @param itemType String for what the values should be cast to: s for string, i for int, f for float (long and double used internally), n for natural sorting strings, ' ' for no sorting.	
	 *                 Uppercase to indicate descending order
	 *                 Example: SiD would be str (desc), integer, double (desc) 
	 * @throws Exception 
	 */
	
	public static void sortValues(String[][] vals, String itemType) throws Exception {
		
		for (int i=0; i<itemType.length(); i++) {
			char cmp = itemType.charAt(i);
			if (cmp != 'S' && cmp != 's' && cmp != 'i' && cmp != 'I' && cmp != 'f' && cmp != 'F' && cmp != 'n' && cmp != ' ') {
				throw new Exception("Unknown sorter type: " + cmp);
			}
		}
		
		Arrays.sort(vals, new Comparator<String[]>(){

			@Override
			public int compare(String[] o1, String[] o2) {
				if (o1.length != o2.length) {
					if (o1.length < o2.length) {
						return -1;
					}
					return 1;
				}
				
				for (int i=0; i< o1.length; i++) {
					String v1 = o1[i];
					String v2 = o2[i];
					char cmp = 's';
					int ret = 0;
					
					if (i < itemType.length()) {
						cmp = itemType.charAt(i);
					}
					
					if (cmp == ' ') {
						ret =  0;							
					} else if (cmp == 's') {
						ret =  v1.compareTo(v2);							
					} else if (cmp == 'S') {
						ret = v2.compareTo(v1);
					} else if (cmp == 'n') {
						ret = StringUtils.naturalCompare(v1, v2);
					} else if (cmp == 'N') {
						ret = StringUtils.naturalCompare(v2, v1);
					} else if (cmp == 'i' || cmp == 'I') {
						long i1 = Long.parseLong(v1);
						long i2 = Long.parseLong(v2);
						if (cmp == 'i' ) {
							ret = Long.compare(i1,  i2);
						} else {
							ret = Long.compare(i2,  i1);
						}
					} else if (cmp == 'f' || cmp == 'F') {
						double i1 = Double.parseDouble(v1);
						double i2 = Double.parseDouble(v2);
						if (cmp == 'i' ) {
							ret = Double.compare(i1,  i2);
						} else {
							ret = Double.compare(i2,  i1);
						}
					}
					
					if (ret != 0) {
						return ret;
					}
				}
				
				return 0;
			}});
		
	}

}
