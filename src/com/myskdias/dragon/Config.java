package com.myskdias.dragon;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;

public abstract class Config {

	protected Logger logger;
	
	public Config() {
	}
	
	public abstract String getChapterTitle(Document d);
	
	public abstract File getNextFile(Document doc, File current) throws FileNotFoundException;
	
	public abstract ArrayList<String> getText(Document doc);
	
	public void setLogger(Logger logger) {
		this.logger = logger;
	}
	
	public Logger getLogger() {
		return logger;
	}
	
}
