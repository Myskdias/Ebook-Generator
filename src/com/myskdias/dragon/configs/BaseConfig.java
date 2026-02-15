package com.myskdias.dragon.configs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import com.myskdias.dragon.Config;

public class BaseConfig extends Config {
	//CALIBRE

	protected String bTitle;
	protected String bNextFile;
	protected String bText;
	protected OrgMode mode;
	protected TextType textType;
	
	public BaseConfig(String bTitle, String bNextFile, String bText, OrgMode mode, TextType textType) {
		this.bTitle = bTitle;
		this.bNextFile = bNextFile;
		this.bText = bText;
		this.mode = mode;
		this.textType = textType;
	}

	@Override
	public String getChapterTitle(Document doc) {
		Element elmt = doc.body().select(bTitle).first();//"a"/*"a.chapter-title"*/
		if(elmt == null) {
			logger.warning("Unable to get the chapter title of "+doc.title());
			return "";
		}
//		String title = elmt.attr("title");
		String title = null;
		if(elmt.hasText()) {
			title = elmt.text().replace("Chapter", "").substring(1);
//			System.out.println((int)title.charAt(7));
//			System.out.println((int)(" ".charAt(0)));
//			&zwnj;
		}
//		System.out.println("#getChapterTitle "+title);
		if(title == null) {
			logger.warning("The title of "+doc.title()+" is null");
			return "";
		} 
		return title;
	}


	@Override
	public File getNextFile(Document doc, File current) throws FileNotFoundException {
		Element elmt = doc.body().select(bNextFile).first();//"a#btn next_page"/*"a#next_chap.btn.btn-success"*/
		if(elmt == null) {
			logger.warning("Unable to get the next fileName of "+doc.title());
			throw new FileNotFoundException("Unable to find the file");
		}
		String fileName = elmt.attr("href");
		if(fileName == null) {
			logger.warning("The new fileName of "+doc.title()+" is null");
			throw new FileNotFoundException("Unable to find the file (tile is null)");
		}
		File f = mode.getFile(fileName, current);
//		System.out.println("#getNextFile "+f.getAbsolutePath());
		return f;
//		return mode.getFile(fileName, current);
		
		//Potentielement a modifier
//		fileName = fileName.substring(2);
//		return new File(current.getParentFile().getParent() +fileName);
		//FIn modif
	}

	@Override
	public ArrayList<String> getText(Document doc) {
		ArrayList<String> text = new ArrayList<String>();
		//A modif
		Elements elmts = doc.body().select(bText);//div#chapter-c.chapter-c
		//Fin modif
		if(elmts == null) {
			logger.warning("Unable to get the text of "+doc.title());
			return text;
		}
//		Element elmt = elmts.first();
//		for(Node n : elmt.childNodes()) {
//			if(n instanceof TextNode) {
//				text.add(((TextNode) n).text());
////				trace(((TextNode) n).text());
//			}
//		}
		
//		for(Element e : elmts.first().getElementsByTag("p")) {
//			//A modif
//			System.out.println("--------------------------------------");
//			System.out.println(e.text()+"<br/>");
//			text.add(e.text());
////			text.add("<br/>");
//			//Fin modif
//		}
//		
		
		textType.getText(elmts, text);
		return text;
	}
	
	public static enum OrgMode {
		
		SAME {
			@Override
			public File getFile(String fileName, File current) {
//				String a = fileName.substring(0, fileName.lastIndexOf('.'));//fileName sans l'extension (ici .html) inutile x)
				File parent = current.getParentFile();
				if(fileName.startsWith("../")) {
					parent = parent.getParentFile();
					fileName = fileName.substring(3, fileName.length());
				}
				return new File(parent, fileName);
			}
		},
		PACKAGED {
			@Override
			public File getFile(String fileName, File current) {
				fileName = fileName.substring(2);
				
				return new File(current.getParentFile().getParent() +fileName);
			}
		},
		;
		
		private OrgMode() { }
		
		public abstract File getFile(String fileName, File current);
		
	}
	
	public static enum TextType {
		
		BASIC {

			@Override
			public void getText(Elements elmts, ArrayList<String> text) {
				Element es = elmts.first();
				if(es == null) {
					System.out.println(elmts.hasText());
					System.out.println("null");
				}
				for(Element e : elmts.first().getElementsByTag("p")) {
					//A modif
					text.add(e.text());
//					text.add("<br/>");
					//Fin modif
				}
			}
			
		},
		STRANGE {
			@Override
			public void getText(Elements elmts, ArrayList<String> text) {
				Element elmt = elmts.first();
				for (Node n : elmt.childNodes()) {
					if (n instanceof TextNode) {
						text.add(((TextNode) n).text());
//					trace(((TextNode) n).text());
					}
				}

			}
		},
		BASIC_PLUS {
		    @Override
		    public void getText(Elements elmts, ArrayList<String> text) {
		        Element root = elmts.first();
		        if (root == null) return;

		        for (Element p : root.getElementsByTag("p")) {

		            // Clone pour pouvoir nettoyer sans toucher au doc original
		            Element clean = p.clone();

		            // Enlève le bruit potentiel DANS les <p> (au cas où)
		            clean.select("script, style, iframe, ins, button").remove();

		            // Optionnel : on garde juste le texte des liens (souvent des "." / nav)
		            clean.select("a").unwrap();

		            // Si le <p> contient des blocs (ex: <h4> dans un <p>), mieux vaut sortir du texte
		            // sinon tu vas te retrouver avec <p><h4>...</h4></p> (XHTML pas top)
		            if (!clean.select("h1,h2,h3,h4,h5,h6,div,table,ul,ol,li").isEmpty()) {
		                String t = clean.text().trim();
		                if (!t.isEmpty()) text.add(t);
		                continue;
		            }

		            // ICI: on garde les balises inline (<i>, <em>, <b>, <strong>, etc.)
		            String html = clean.html().trim();

		            // Petit filtre anti-paragraphes vides
		            if (html.isEmpty() || html.equals("&nbsp;")) continue;

		            // Optionnel : normaliser i/b vers em/strong (souvent mieux en EPUB)
		            html = html.replace("<i>", "<em>").replace("</i>", "</em>")
		                       .replace("<b>", "<strong>").replace("</b>", "</strong>");

		            text.add(html);
		        }
		    } 
		}
		
		;
		
		private TextType() { }
		
		public abstract void getText(Elements elmts, ArrayList<String> text);
		
	}

}
