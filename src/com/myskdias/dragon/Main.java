package com.myskdias.dragon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.myskdias.dragon.configs.BaseConfig;
import com.myskdias.dragon.configs.Data;

public class Main {
	
	//To modify
	public static final String name = "BeyondTheTimescape";
	public static final String author = "ErGen";
	public static final BaseConfig config = Data.REVERENT_INSANITY;
	//End to modify
	
	public static File gen = new File("C:\\Users\\mathi\\Documents\\webnovel\\books\\"+name.replace(" ", "-")+"\\ebook\\");
	public static File dest = new File("C:\\Users\\mathi\\Documents\\webnovel\\books\\"+name.replace(" ", "-")+"\\generate\\");
	
	//To modify
	public static File f = new File("C:\\Users\\mathi\\Documents\\webnovel\\scrapped\\BeyondTheTimescape\\novelfull.net\\beyond-the-timescape\\chapter-1-living.html");
	
	public static final int chapterNumber = -1;
	//End to modify
	
	public static Logger logger;
	
	@SuppressWarnings("unused")
	public static void main(String[] args) {
			logger = Logger.getAnonymousLogger();
			config.setLogger(logger);
			info("Starting HTML Creator...");
			info("==================================================================");
			info("Trying to generate chapters of the novel "+name + " written by "+author);
			info("Src: "+f.getAbsolutePath());
			info("dest: "+dest.getAbsolutePath());
			
			if(chapterNumber == -1) {
				info("Trying to generate as many chapter as possible");
			} else {
				info("Trying to generate "+chapterNumber+" chapters");
			}
			
			info("==================================================================");
			info("Clearing generate directory...");
			Path path = dest.toPath();

			try {
				Files.walk(path)
				     .sorted(Comparator.reverseOrder())
				     .forEach(p -> {
				         try {
				             Files.delete(p);
				         } catch (IOException e) {
				             e.printStackTrace();
				         }
				     });
			} catch (IOException e) {
				e.printStackTrace();
			}
			dest.mkdirs();
			info("==================================================================");
			info("Starting the generation...");
			MetaInfos metaInfos = new MetaInfos(name, author, new File(dest, "meta\\"));
			create(f, chapterNumber, metaInfos);
			metaInfos.generate();
			info("Finish generating chapter");
			generateZip();
	}
	
	//Normalement rien a modif
	public static void write(File f, String chapterTitle, ArrayList<String> psl) {
		try {
			PrintWriter writer = new PrintWriter(f, StandardCharsets.UTF_8);

			writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" + 
					"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\r\n" + 
					"<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\">\r\n" + 
					"  <head>\r\n" + 
					"    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\r\n" + 
					"    <link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/>\r\n" + 
					"    <title>"/*+name+" - "*/+chapterTitle+"</title>\r\n" + 
					"  </head>\r\n" + 
					"  <body>\r\n" + 
					"    <div class=\"header\">\r\n" + 
					"      <h2>Chapter "+chapterTitle+"</h2>\r\n" + 
					"    </div>\r\n" + 
					"    <p>\r\n" + 
					"      <br/>\r\n" + 
					"    </p>\r\n" + 
					"    <hr/>\r\n");
			writer.write("<p><br/></p>\r\n");
			for(String s : psl) {
				writer.write("<p>"+s+"</p>\r\n");
			}
			writer.write("<p><hr/></p>\r\n");
			writer.write("</body>\r\n" + 
					"</html>");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//Normalement rien a modif dans cette method
	public static void create(File f, int a, MetaInfos metaInfos) {
//		info("Tour "+a);
		Document doc = null;
		boolean b = true;
		try {
		if(f == null || !f.exists()) {
			if(f != null) System.out.println(f.getAbsolutePath());
			logger.severe("Can't use a \'null\' file...");
			metaInfos.generate();
			return;
		}
		if (a == 0) {
			metaInfos.generate();
			return;
		}
		try {
			doc = Jsoup.parse(f, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String chapterTitle = transform(getChapterTitle(doc).replace("-", ""));
		info("Generating "+chapterTitle+" ...");
		ArrayList<String> fullText = getText(doc);
//		info(chapterTitle);
		if(chapterTitle.isEmpty()) {
			chapterTitle = a+"";
		}
		//MODIF
		
		String fTitle = syntaxe(chapterTitle);/*chapterTitle.replace(" ", "-").split(":")[0]+".html";*/
//		info(fTitle);
		//FIN MODIF
		
		//Le fichier ou sera ecrit le chapitre
		//Custom title car pour DE toutes les files s'appele index.html
		//File nChapterFile = new File(dest.getAbsolutePath(), /*f.getName()*/fTitle);
//		System.out.println(nChapterFile.getAbsolutePath());
		//Verification que le fichier existe et est vide --> si existe --> delete --> recréer deriere
		/*if (nChapterFile.exists()) {
			
			//nChapterFile.delete();
		}*/
		File nChapterFile = getSuitableFile(dest, fTitle);
		try {
			nChapterFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		metaInfos.addChapter(chapterTitle, /*f.getName()*/nChapterFile.getName());
		write(nChapterFile, chapterTitle, transform(fullText));
		} catch(Exception e) {
			b = false;
		}
		if(b) {
			try {
				create(getNextFile(doc, f), a-1, metaInfos);
			} catch (FileNotFoundException e) {
				
			}
		}
		
	}
	
	private static File getSuitableFile(File root, String fTitle) {
		if(fTitle.isEmpty()) {
			fTitle = "c";
		}
		File nChapterFile = new File(dest.getAbsolutePath(), /*f.getName()*/fTitle+".html");
		int i = 0;
		while(nChapterFile.exists()) {
			i++;
			nChapterFile = new File(dest.getAbsolutePath(), fTitle+"_"+i+".html");
		}
		if(i > 0) {
			info("Detected splitted chapter ! ("+nChapterFile.getName()+")");
		}
		
		return nChapterFile;
		
	}

	/**
	 * 
	 * @param chapterTitle
	 * @return le vrai titre sans le nom du livre avant 
	 */
	private static String syntaxe(String chapterTitle) {
		boolean a = false;
		int e = 0;
		int ef = 0;
		char[] cl = chapterTitle.toCharArray();
		for(int i = 0; i < cl.length; i++) {
			char c = cl[i];
			
			if(!isNumber(c)) {
				if(a) {
					ef = i;
					break;
				}
			} else {
				ef = i+1;
				if(!a) {
					e = i;
					a = true;
				}
				
			}
		}
		return chapterTitle.substring(e, ef);
	}

	private static boolean isNumber(char c) {
		return Character.isDigit(c);
	}
	
	private static void info(String text) {
		System.out.println(text);
	}
	
	private static void trace(String text) {
		System.out.println(text);
	}
	
	private static String getChapterTitle(Document doc) {
		
//		Element elmt = doc.body().select(/*To modify*/"a"/*"a.chapter-title"*/).first();
//		if(elmt == null) {
//			logger.warning("Unable to get the chapter title of "+doc.title());
//		}
////		String title = elmt.attr("title");
//		String title = elmt.text().replace("Chapter ", "");
//		if(title == null) {
//			logger.warning("The title of "+doc.title()+" is null");
//			return "";
//		} 
//		return title;
		return config.getChapterTitle(doc);
	}
	
	private static File getNextFile(Document doc, File current) throws FileNotFoundException {
//		Element elmt = doc.body().select(/*To modify*/"a#btn next_page"/*"a#next_chap.btn.btn-success"*/).first();
//		if(elmt == null) {
//			logger.warning("Unable to get the next fileName of "+doc.title());
//		}
//		String fileName = elmt.attr("href");
//		if(fileName == null) {
//			logger.warning("The new fileName of "+doc.title()+" is null");
//			return null;
//		}
//		//Potentielement a modifier
//		fileName = fileName.substring(2);
//		return new File(current.getParentFile().getParent() +fileName);
//		//FIn modif
		return config.getNextFile(doc, current);
	}
	
	private static ArrayList<String> getText(Document doc) {
//		ArrayList<String> text = new ArrayList<String>();
//		//A modif
//		Elements elmts = doc.body().select("div#chapter-c.chapter-c");
//		//Fin modif
//		if(elmts == null) {
//			logger.warning("Unable to get the text of "+doc.title());
//			return text;
//		}
//		Element elmt = elmts.first();
//		for(Node n : elmt.childNodes()) {
//			if(n instanceof TextNode) {
//				text.add(((TextNode) n).text());
////				trace(((TextNode) n).text());
//			}
//		}
//		for(Element e : elmts) {
//			
//			//A modif
//			trace(e.text());
//			text.add(e.text());
//			//Fin modif
//		}
//		
//		return text;
		return config.getText(doc);
	}
	
	public static void generateZip() {
		gen.mkdirs();
		File book = new File(gen, name.replace(" ", "-")+".zip");
		if(book.exists()) {
			book.delete();
		}
		try {
			book.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileOutputStream os;
		try {
			os = new FileOutputStream(book);
			ZipOutputStream zos = new ZipOutputStream(os);
			
			zip("mimetype", zos);
			zip("OEBPS/styles.css", zos);
			zip("OEBPS/Styles/CoverPage.css", zos);
			zip("META-INF/container.xml", zos);
			
			List<File> files = getAllFile(dest);
			for(File f : files) {
				writeZipEntry(getName(f), f, zos);
			}
			
			try {
				zos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private static String getName(File f) {
//		return f.getAbsolutePath().replace(dest.getAbsolutePath()+"\\", "");
		return f.getName();
	}
	
	private static ArrayList<File> getAllFile(File file) {
		ArrayList<File> list = new ArrayList<File>();

		for (File f : file.listFiles()) {
			if (f.isDirectory()) {
				list.addAll(getAllFile(f));
			} else {
				list.add(f);
			}
		}
		return list;
	}
	
	private static File getFile(String path) {
		File file = new File(Main.class.getClassLoader().getResource(path).getFile());
		return file;
	}
	
	private static void zip(String name, ZipOutputStream zos) {
		File f = getFile(name);
		writeZipEntry(name, f, zos);
	}
	
	private static void writeZipEntry(String n, File src, ZipOutputStream zos) {
//		String rel = dest.getAbsolutePath();
//		String name = src.getAbsolutePath().replace(rel, "");
		ZipEntry ze = new ZipEntry(n);
		try {
			zos.putNextEntry(ze);
			byte[] data = Files.readAllBytes(src.toPath());
			zos.write(data);
			zos.closeEntry();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static final String sp = ((char)8204)+"";
	
	
	/**‘
	 * Replace les caractères non reconnues par le format epub
	 * @param psl
	 * @return
	 */
	public static ArrayList<String> transform(ArrayList<String> psl) {
		ArrayList<String> n = new ArrayList<String>();
		for(String s : psl) {
			n.add(s.replace("—","&#8212;").replace(sp, "").replace("“", "&ldquo;").replace("”", "&rdquo;").replace("‘", "&lsquo;").replace("’", "&rsquo;").replace("–", "&ndash;").replace("…", "&hellip;"));
		}
		return n;
	}	
//	&zwnj;
	public static String transform(String s) {
		return s.replace("“", "&ldquo;").replace("”", "&rdquo;").replace(sp, "").replace("‘", "&lsquo;").replace("’", "&rsquo;").replace("–", "&ndash;").replace("…", "&hellip;");
	}
	
	public List<String> getLine(File f) {
		List<String> lines = null;
		try {
			lines = Files.readAllLines(f.toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lines;
	}

//	public List<String> analize(List<String> lines) {
//		
//		return null;
//		
//	}
//	
}
