package com.myskdias.dragon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;

public class NCX {

	private int i = 1;
	private String title;
	private String author;
	private StringBuilder content = new StringBuilder();
	private File f;
	
	public NCX(String title, String author, File f) {
		this.title = title;
		this.author = author;
		this.f = f;
		if(!f.exists()) {
			f.getParentFile().mkdirs();
			try {
				f.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\"\r\n" + 
				"  \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">\r\n" + 
				"<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\" xml:lang=\"en\" dir=\"ltr\">\r\n" + 
				"	<head>\r\n" + 
				"		<meta name=\"dtb:uid\" content=\"https://www.asianovel.com/download/epub/9adb93f0-7019-11e9-8442-d9f87eb30806\" />\r\n" + 
				"		<meta name=\"dtb:depth\" content=\"3\" />\r\n" + 
				"		<meta name=\"dtb:totalPageCount\" content=\"0\" />\r\n" + 
				"		<meta name=\"dtb:maxPageNumber\" content=\"0\" />\r\n" + 
				"	</head>\r\n" + 
				"\r\n" + 
				"	<docTitle>\r\n" + 
				"		<text>"+title+"</text>\r\n" + 
				"	</docTitle>\r\n" + 
				"\r\n" + 
				"	<docAuthor>\r\n" + 
				"		<text>"+author+"</text>\r\n" + 
				"	</docAuthor>\r\n" + 
				"\r\n" + 
				"	<navMap>"
				);
	}
	
	public void addChapter(String title, String filePath) {
		content.append("<navPoint id=\"chapter"+i+"\" playOrder=\""+i+"\">\r\n" + 
				"			<navLabel>\r\n" + 
				"				<text>"+title+"</text>\r\n" + 
				"			</navLabel>\r\n" + 
				"			<content src=\""+filePath+"\" />\r\n" + 
				"		</navPoint>\r\n"
				);
		i++;
	}
	
	public void generate() {
		content.append("	</navMap>\r\n" + 
				"</ncx>\r\n" + 
				"");
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(f);
			writer.write(content.toString());
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static class A<K, V> implements Entry<K, V> {

		K k;
		V v;
		
		public A(K k, V v) {
			this.k = k;
			this.v = v;
		}
		
		@Override
		public K getKey() {
			return k;
		}

		@Override
		public V getValue() {
			return v;
		}

		@Override
		public V setValue(V value) {
			return v;
		}
		
	}
	
}
