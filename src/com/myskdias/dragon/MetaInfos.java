package com.myskdias.dragon;

import java.io.File;

public class MetaInfos {

	private NCX ncx;
	private OPF obf;
	
	public MetaInfos(String title, String author, File f) {
		this.ncx = new NCX(title, author, new File(f, "book.ncx"));
		this.obf = new OPF(title, author, new File(f, "book.opf"));
	}
	
	public void addChapter(String title, String filePath) {
		ncx.addChapter(title, filePath);
		obf.add(filePath);
	}
	
	public void generate() {
		ncx.generate();
		obf.generate();
	}
	
}
