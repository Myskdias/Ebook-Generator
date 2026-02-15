package com.myskdias.dragon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

public class OPF {

	private int i = 1;
	private String title;
	private String author;
	private StringBuilder content = new StringBuilder();
	private File f;
	private StringBuilder temp = new StringBuilder();
	
	public OPF(String title, String author, File f) {
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
		content.append(
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" + 
				"<package xmlns=\"http://www.idpf.org/2007/opf\"\r\n" + 
				"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + 
				"	xmlns:opf=\"http://www.idpf.org/2007/opf\"\r\n" + 
				"	xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\r\n" + 
				"	unique-identifier=\"BookId\" version=\"2.0\">\r\n" + 
				"	<metadata>\r\n" + 
				"		<dc:subject>"+title+"</dc:subject>\r\n" + 
				"		<dc:title>"+title+"</dc:title>\r\n" + 
				"		<dc:language>en</dc:language>\r\n" + 
				"		<dc:identifier id=\"uuid_id\" opf:scheme=\"uuid\">a75b1aac-c3e3-40e6-b4da-7dd900e29132</dc:identifier>\r\n" + 
				"		<dc:date opf:event=\"publication\">2019-05-06T16:15:05.000000+00:00</dc:date>\r\n" + 
				"		<dc:creator opf:file-as=\"Cocooned Cow\" opf:role=\"aut\">"+author+"</dc:creator>\r\n" +
				"		<meta name=\"calibre:series\" content=\"Series\" />\r\n" + 
				"		<meta name=\"calibre:series_index\" content=\""+title+"\" />\r\n" + 
				"		<meta name=\"cover\" content=\"CoverImage\" />\r\n" + 
				"	</metadata>\r\n" + 
				"\r\n" + 
				"	<manifest>\r\n" + 
				"		<item id=\"ncx\" href=\"book.ncx\" media-type=\"application/x-dtbncx+xml\" />\r\n" + 
				"		<item id=\"css_css1\" href=\"styles.css\" media-type=\"text/css\" />\r\n" + 
				"		<item id=\"css_CoverPageCss\" href=\"Styles/CoverPage.css\" media-type=\"text/css\" />\r\n" + 
				"		<item id=\"CoverImage\" href=\"images/Cover.jpg\" media-type=\"image/jpeg\" />\r\n" + 
				"		<item id=\"ref_cover\" href=\"CoverPage.xhtml\" media-type=\"application/xhtml+xml\" />\r\n" + "\r\n"
				);
		temp.append("	</manifest>\r\n" + 
				"\r\n" + 
				"	<spine toc=\"ncx\">\r\n"
				);
	}
	
	public void add(String filePath) {
		content.append("		<item id=\"chapter"+i+"\" href=\""+filePath+"\" media-type=\"application/xhtml+xml\" />\r\n");
		temp.append("		<itemref idref=\"chapter"+i+"\" />\r\n");
		i++;
	}
	
	public void generate() {
		temp.append("	</spine>\r\n" + 
				"\r\n" + 
				"	<guide>\r\n" + 
				"		<reference type=\"cover\" title=\"CoverPage\" href=\"CoverPage.xhtml\" />\r\n" + 
				"		<reference type=\"text\" title=\"Introduction\" href=\"cover.html\" />\r\n" + 
				"	</guide>\r\n" + 
				"</package>");
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(f);
			writer.write(content.toString());
			writer.write(temp.toString());
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
