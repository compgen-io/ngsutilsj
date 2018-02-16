package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.vcf.support.EnsemblREST;
import io.compgen.ngsutils.vcf.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class EnsemblVEP extends AbstractBasicAnnotator {
	
	private class AnnotationValues {
		private Map<String, List<String>> vals = new HashMap<String, List<String>>();
		public void add(String k, String val) {
			if (!vals.containsKey(k)) {
				vals.put(k, new ArrayList<String>());
			}
			vals.get(k).add(val);
		}
		public Set<String> keySet() {
			return vals.keySet();
			
		}
//
//		public void dump() {
//			for (String k: vals.keySet()) {
//				System.err.println(k+" => " + StringUtils.join(", ", vals.get(k)));
//			}
//		}
		
		public String get(String k) {
			if (vals.containsKey(k)) {
				return StringUtils.join(",",vals.get(k));
			}
			return VCFAttributeValue.MISSING.toString();
		}
		public int size(String k) {
			if (vals.containsKey(k)) {
				return vals.get(k).size();
			}
			return 0;
		}
	}
	
	protected String hostname = null;
	protected String endpoint = "vep";
	protected String species = "homo_sapiens";
	protected EnsemblREST rest;
	
	public EnsemblVEP(String species, String cacheFile, String hostname) throws IOException {
		if (species != null) {
			this.species = species;
		}
		if (cacheFile != null) {
			EnsemblREST.setCache(cacheFile);
		}
		
		rest = EnsemblREST.getServer(hostname);
	}
	
	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		try {
			header.addInfo(VCFAnnotationDef.info("CG_VEP_polyphen_score".toUpperCase(), ".", "String", "VEP annotation - polyphen_score"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_sift_score".toUpperCase(), ".", "String", "VEP annotation - sift_score"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_sift_prediction".toUpperCase(), ".", "String", "VEP annotation - sift_prediction"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_polyphen_prediction".toUpperCase(), ".", "String", "VEP annotation - polyphen_prediction"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_impact".toUpperCase(), ".", "String", "VEP annotation - impact"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_amino_acids".toUpperCase(), ".", "String", "VEP annotation - amino_acids"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_protein_start".toUpperCase(), ".", "String", "VEP annotation - protein_start"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_protein_end".toUpperCase(), ".", "String", "VEP annotation - protein_end"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_biotype".toUpperCase(), ".", "String", "VEP annotation - biotype"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_gene_id".toUpperCase(), ".", "String", "VEP annotation - gene_id"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_gene_symbol".toUpperCase(), ".", "String", "VEP annotation - gene_symbol"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_transcript_id".toUpperCase(), ".", "String", "VEP annotation - transcript_id"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_id".toUpperCase(), ".", "String", "VEP annotation - id"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_clin_sig".toUpperCase(), ".", "String", "VEP annotation - clin_sig"));
			header.addInfo(VCFAnnotationDef.info("CG_VEP_most_severe_consequence".toUpperCase(), ".", "String", "VEP annotation - most_severe_consequence"));
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		
		for (String alt: record.getAlt()) {
			String url = endpoint + "/"+
					     species + "/region/" +
					     record.getChrom().replace("chr", "") +
					     ":" + record.getPos()+"-";
			
			if (record.getRef().length() == 1) {
				if (alt.length() == 1) {
					// snv
					url += record.getPos()+":1/"+alt;
				} else {
					// insert
					url += record.getPos()+":1/"+alt;

				}				
			} else {
				// deletion
				url += record.getPos()-alt.length()+":1/-";
			}

			url += "?content-type=application/json";
			try {
				JsonValue obj = rest.call(url, 10*60); // wait for 10 min
				AnnotationValues values = new AnnotationValues();

				if (obj != null) {
					if (obj.isArray()) {
						JsonArray ar = obj.asArray();
						for (JsonValue val: IterUtils.wrap(ar.iterator())) {
							JsonObject obj1 = val.asObject();
							for (String name: obj1.names()) {
								if (name.equals("colocated_variants")) {
									JsonArray ar1 = obj1.get(name).asArray();
//									System.err.println("    " + name +" =>");
									for (JsonValue val1: IterUtils.wrap(ar1.iterator())) {
										JsonObject obj2 = val1.asObject();
										for (String name2: obj2.names()) {
											switch(name2) {
											case "id":
											case "clin_sig":
												values.add("colocated_"+name2, obj2.get(name2).toString());
												break;
											default:
												// pass
											}
										}										
									}
									
								} else if (name.equals("transcript_consequences")) {
									JsonArray ar1 = obj1.get(name).asArray();
//									System.err.println("    " + name +" =>");
									for (JsonValue val1: IterUtils.wrap(ar1.iterator())) {
										JsonObject obj2 = val1.asObject();
										for (String name2: obj2.names()) {
											switch(name2) {
											case "polyphen_score":
											case "sift_score":
											case "sift_prediction":
											case "polyphen_prediction":
											case "impact":
											case "amino_acids":
											case "protein_start":
											case "protein_end":
											case "biotype":
											case "gene_id":
											case "gene_symbol":
											case "transcript_id":
												values.add(name2, obj2.get(name2).toString());
												break;
											default:
												// pass
											}
										}										
									}
									
								} else {
									switch(name) {
									case "most_severe_consequence":
										values.add(name, obj1.get(name).toString());
										break;
									default:
										// pass
									}
								}
							}
						}
					}
//					values.dump();
					for (String k: values.keySet()) {
						if (values.size(k) > 0) {
							record.getInfo().put(("CG_VEP_"+k).toUpperCase(), new VCFAttributeValue(values.get(k)));
						}
					}
				}
			} catch (IOException e) {
				throw new VCFAnnotatorException(e);
			}
		}
	}
	
	public void close() {
		EnsemblREST.cleanup();
	}
}