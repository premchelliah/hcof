package hcof;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import com.google.common.base.Splitter;

/**
 * ASSUMPTIONS
 * 1. All static file references should point to the source domain
 * 2. JS/CSS files should not refer exact file names in text strings
 * 3. Non-removable comments (starting with /*!) will also be removed
 * 4. Use the entire source code as an input to the tool
 * 5. Comment "debugger;" statements or eval them "eval("debugger");", otherwise yui compressor ignores the file
 * 
 * STEPS
 * 1. Recursively scan path for binary files and create a copy with MD5 name 
 * 2. Search & Replace all text files with newly created MD5 name  
 * 3. Recusrively scan path for text files and create a directed graph (vertex - files, edge - references)
 * 4. From graph, create copy with MD5 name for all vertexes (a) with no outgoing edges 
 * 5. Search & Replace all vertexes (b) with incoming edges into vertexes (a) 
 * 6. Remove vertexes (a) from graph and repeat steps 4-6
 * 7. Update all server units (java, jsp, xml, xsl) with MD5 resource names v
 * 
 * */

public class HttpCacheOptimizer {

	private static List<Character> metaList = new ArrayList<Character>();
	private static Properties prop = new Properties();
	private static List<String> allfiles = new ArrayList<String>();
	private static DirectedGraph<String, DefaultEdge> graph = new SimpleDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
	private static Map<String, String> textFileMap = new HashMap <String, String>();
	private static String str, lb, la = null;
	private static Map<Pattern, String> processedFiles =  new HashMap<Pattern, String>();
	private static Map<String, File> files = new HashMap<String, File>();
	private static Set<String> duplicates = new HashSet<String>();
	private static Splitter splitter = Splitter.on(',').omitEmptyStrings().trimResults();

	public static void main(String[] args) {

		try {

			long time = System.currentTimeMillis();

			prop.load(HttpCacheOptimizer.class.getClassLoader().getResourceAsStream("config.properties"));
			allfiles.addAll(split(prop.getProperty("clienttextfiles")));
			allfiles.addAll(split(prop.getProperty("clientbinaryfiles")));

			loadFilenameSpecialCharPattern();

			createFileList();

			createMD5Binary();

			createGraph();

			fixTextFiles();

			fixSourceFiles();

			System.out.println("Http Cache Optimization took " + DurationFormatUtils.formatDuration(System.currentTimeMillis() - time, "HH:mm:ss:SS"));

		} catch (Exception e) {

			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private static void createFileList() {

		Collection<File> flist = listFiles(allfiles);
		for(File file : flist) {

			str = file.getName();
			if(!duplicates.contains(str) && !files.containsKey(str)) {

				files.put(str, file);
			}
			else {

				duplicates.add(str);
				files.remove(str);
			}
		}
		System.out.println("total files " + flist.size());
		System.out.println("files with unique name " + files.size());
		if(!duplicates.isEmpty()) System.out.println("files with same name --> " + duplicates);
	}

	private static void createMD5Binary() throws IOException {

		for(File file : listFiles(split(prop.getProperty("clientbinaryfiles")))) {

			if(duplicates.contains(file.getName())) continue;
			createMD5File(file);
		}
	}

	private static void createGraph() throws IOException, InterruptedException {

		String fpath = null;
		boolean flag = false;

		for(File file : listFiles(split(prop.getProperty("clienttextfiles")))) {

			str = file.getName();
			fpath = file.getAbsolutePath();

			flag = duplicates.contains(str); 
			if(!flag) {

				FileUtils.writeStringToFile(file, FileUtils.readFileToString(file).replaceAll("/\\*!", "/*"));
			}

			System.out.println("minifying " + fpath);
			new ProcessBuilder("java", prop.getProperty("Xssn"), "-jar", prop.getProperty("yuicompressorpath"), "-o", fpath, fpath).start().waitFor();
			if(flag) continue;

			graph.addVertex(str);
			textFileMap.put(str, FileUtils.readFileToString(file));
		}

		Pattern pattern = null;
		for(String out : textFileMap.keySet()) {

			pattern = Pattern.compile(lb + replaceMeta(out) + la);
			for(Map.Entry<String, String> entry : textFileMap.entrySet()) {

				if(pattern.matcher(entry.getValue()).find()) {

					try {

						graph.addEdge(entry.getKey(), out);
						System.out.println(entry.getKey() + " has reference(s) to " + out);
					}
					catch(IllegalArgumentException e) {

						if(!"loops not allowed".equals(e.getMessage())) throw e;
						System.out.println(entry.getKey() + " has reference(s) to itself");
					}
				}
			}
		}
	}

	private static void createMD5File (File file) throws IOException {

		File newFile = getMD5File(file, null);

		FileUtils.copyFile(file, newFile);
		processedFiles.put(Pattern.compile(lb + replaceMeta(file.getName()) + la), newFile.getName());
	}

	private static void fixTextFiles() throws IOException {

		List<String> list = new ArrayList<String>();
		if(graph.vertexSet().isEmpty()) return;
		File file, newFile = null;
		String content = null;

		for(String v : graph.vertexSet()) {

			if(graph.outgoingEdgesOf(v).isEmpty()) {

				System.out.println("processing file " + v);

				content = textFileMap.remove(v);

				for(Entry<Pattern, String> e : processedFiles.entrySet()) {

					content = e.getKey().matcher(content).replaceAll(e.getValue());
				}

				file = files.get(v);
				newFile = getMD5File(file, content);
				FileUtils.writeStringToFile(newFile, content);

				processedFiles.put(Pattern.compile(lb + replaceMeta(file.getName()) + la), newFile.getName());

				list.add(v);
			}
		}
		graph.removeAllVertices(list);
		fixTextFiles();
	}

	private static void fixSourceFiles() throws IOException {

        String oldContent, newContent = null;

		for(File file : listFiles(split(prop.getProperty("servertextfiles")))) {

			oldContent = newContent = FileUtils.readFileToString(file);

			for(Entry<Pattern, String> e : processedFiles.entrySet()) {

				newContent = e.getKey().matcher(newContent).replaceAll(e.getValue());				
			}
            if(!newContent.equals(oldContent)) {

            	FileUtils.writeStringToFile(file, newContent);
				System.out.println("processed file " + file.getName());
            }
		}
	}

	private static File getMD5File(File file, String content) throws IOException {

		String absPath = file.getAbsolutePath();
		int tempInt = absPath.lastIndexOf('.');

		return new File(absPath.substring(0, tempInt) + "_" + (content == null ? 
					DigestUtils.md5Hex(new FileInputStream(absPath)) : 
					DigestUtils.md5Hex(content)) + absPath.substring(tempInt));
	}

	public static Collection<File> listFiles(List<String> list) {

		Collection<File> flist = FileUtils.listFiles(new File (prop.getProperty("root")), new SuffixFileFilter(list, IOCase.INSENSITIVE), TrueFileFilter.INSTANCE);
		Iterator<File> iter = flist.iterator();
		int pos = 0;
		while(iter.hasNext()) {

			str = iter.next().getName();
			pos = str.lastIndexOf('_');
			if (pos != -1 && str.length() > pos + 33 && '.' == str.charAt(pos + 33)) iter.remove();
		}
		return flist;
	}

	private static void loadFilenameSpecialCharPattern() {

		List<Character> list = new ArrayList<Character> ();
		CharSequence cs = null;
    	Character c = null;
    	String pat = null;
    	la = "(?![";
    	lb = "(?<![";

		for(File file : listFiles(allfiles)) {

			cs = file.getName();
			if (cs != null && cs.length() != 0) {

	        	int sz = cs.length();
	        	for (int i = 0; i < sz; i++) {

	        		c = cs.charAt(i);
	        		if (!Character.isLetterOrDigit(c)) {

	        			if(!list.contains(c)) {

	        				list.add(c);

	        				switch(c) {

	        					case '^': case '[': case '.': case '$': case '{': case '*': case '(': case '\\':
	        					case '+': case ')': case '|': case '?' : case '<': case '>': case '-': case ']': case '}':
	        						pat = "\\" + c;
	        						metaList.add(c);
	        						break;
	        					default :
	        						pat = c.toString();
	        				}
	        				la += pat;
	        				lb += pat;
	        			}
	        		}
	            }
	        }
		}
		la += "])\\b";
		lb += "])\\b";

		System.out.println("lookahead " + la);
		System.out.println("lookbehind " + lb);
	}

	private static List<String> split(String list) {

		List<String> templist = new ArrayList<String>();
		for(String string : splitter.split(list)) {

			templist.add(string);
		}
		return templist;
	}

	private static String replaceMeta(String meta) {

		if(!metaList.isEmpty()) {

			for(Character c : metaList) {

				meta = meta.replaceAll("\\"+c, "\\\\"+c);
			}
		}
		return meta;
	}
}