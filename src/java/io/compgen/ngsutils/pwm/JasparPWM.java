package io.compgen.ngsutils.pwm;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class JasparPWM extends AbstractMotifFinder {
	protected String accn;
	protected String name;
	protected double [][] pwm; // double[a,c,g,t][pos1, pos2, pos3...]

	public JasparPWM(String filename, int pseudo) throws Exception {
		super();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
		
		if (pseudo < 0) {
			pseudo = 0;
		}
		
		String header = reader.readLine();
		if (header.charAt(0) != '>') {
			reader.close();
			throw new Exception("File is not in JASPAR format! (invalid header)");
		}
		
		String[] spl = header.split(" |\t", 2);
		this.accn = spl[0].substring(1); // remove the '>'
		this.name = spl[1];

		String line;
		int[] a = null,c = null,g = null,t = null;

		while ((line = reader.readLine())!=null) {
			spl = line.split("[ |\t]+");
			
			if (spl[0].toUpperCase().equals("A")) {
				a = new int[spl.length-3];
	 			for (int i=0; i< spl.length-3; i++) {
	 				a[i] = Integer.parseInt(spl[i+2]);
	 			}
	 		} else if (spl[0].toUpperCase().equals("C")) {
				c = new int[spl.length-3];
	 			for (int i=0; i< spl.length-3; i++) {
	 				c[i] = Integer.parseInt(spl[i+2]);
	 			}
	 		} else if (spl[0].toUpperCase().equals("G")) {
				g = new int[spl.length-3];
	 			for (int i=0; i< spl.length-3; i++) {
	 				g[i] = Integer.parseInt(spl[i+2]);
	 			}
	 		} else if (spl[0].toUpperCase().equals("T")) {
				t = new int[spl.length-3];
	 			for (int i=0; i< spl.length-3; i++) {
	 				t[i] = Integer.parseInt(spl[i+2]);
	 			}
	 		} else {
	 			System.out.println(line);
	 			System.out.println(spl[0]);
	 			reader.close();
				throw new Exception("File is not in JASPAR format!");
	 		}
		}
		
		reader.close();

		if (a == null || c == null || g == null || t == null) {
			throw new Exception("File is not in JASPAR format! (missing base)");
		}

		// convert the a,c,g,t counts to a matrix
		int[][] counts = new int[][] {a,c,g,t};

		int n = -1;
		for (int i=0; i<counts[0].length; i++) {
			int tmp = 0;
			for (int j=0; j< 4; j++) {
				tmp += counts[j][i]; // order important!
			}
			if (n >0) {
				if (tmp != n) {
					throw new Exception("File is not in JASPAR format! (invalid counts) (expected: "+n +", got:"+tmp+", pos:"+i+")");
				}
			}
			n = tmp;
		}

		total = n;
		
		// convert the counts to a position probability matrix (fractional frequency)
		double[][] ppm = new double[counts.length][counts[0].length];
		for (int i=0; i<counts.length; i++) {
			for (int j=0; j< counts[0].length; j++) {
				ppm[i][j] = ((double)counts[i][j]+pseudo) / (total + (4*pseudo)); // add a pseudo count for each base
			}
		}

		// convert the ppm to a PWM (log-likelihood, with background level correction) 
		pwm = new double[ppm.length][ppm[0].length];
		for (int i=0; i<ppm.length; i++) {
			for (int j=0; j<ppm[0].length; j++) {
				pwm[i][j] = log2(ppm[i][j] / backgroundRates[i]); // calc log2 likelihood, assume background levels as above (60% GC)
			}
		}
		
		// now that we have the final matrix, calculate the number of variations needed for permuting pvalues
//		
//		double combinations = Math.pow(4, pwm[0].length);
//		if (combinations > MAX_PERMUTATION_COUNT) {
//			this.permCount = MAX_PERMUTATION_COUNT;
//		} else {
//			this.permCount = (int) combinations;
//		}
	}
	public String getAccn() {
		return accn;
	}

	public String getName() {
		return name;
	}
	@Override
	public int getLength() {
		return pwm[0].length;
	}


	@Override
	public double calcScore(String seq) throws Exception {
		double ret = 0.0;
		
		for (int i=0; i<pwm[0].length; i++) {
			if (i < seq.length()) {
				switch(seq.charAt(i))
				{
					case 'A':
						ret += pwm[0][i];
						break;
					case 'C':
						ret += pwm[1][i];
						break;
					case 'G':
						ret += pwm[2][i];
						break;
					case 'T':
						ret += pwm[3][i];
						break;
					default:
						throw new Exception("Bad base in sequence: " + seq);
				}
			}
		}
		
		return ret;
	}
	public String writePWM() {
		String ret = "";
		
		ret += "A";
		for (int i=0; i<pwm[0].length; i++) {
			ret += "\t" + pwm[0][i];
		}
		ret += "\n";

		ret += "C";
		for (int i=0; i<pwm[1].length; i++) {
			ret += "\t" + pwm[1][i];
		}
		ret += "\n";

		ret += "G";
		for (int i=0; i<pwm[2].length; i++) {
			ret += "\t" + pwm[2][i];
		}
		ret += "\n";

		ret += "T";
		for (int i=0; i<pwm[3].length; i++) {
			ret += "\t" + pwm[3][i];
		}
		ret += "\n";

		return ret;
	}


}
