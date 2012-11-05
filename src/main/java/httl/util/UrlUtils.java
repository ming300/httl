/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package httl.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * UrlUtils. (Tool, Prototype, ThreadUnsafe)
 * 
 * @author Liang Fei (liangfei0201 AT gmail DOT com)
 */
public class UrlUtils {

	public static final String PROTOCOL_SEPARATOR = "://";

	public static final String PATH_SEPARATOR = "/";

	public static final char PATH_SEPARATOR_CHAR = '/';

	public static final char WINDOWS_PATH_SEPARATOR_CHAR = '\\';

	/**
	 * 关联路径
	 * 
	 * @param templateName
	 * @param relativeName
	 * @return
	 * @throws MalformedURLException
	 */
	public static String relativeUrl(String templateName, String relativeName) throws MalformedURLException {
		if (templateName == null || templateName.length() == 0 
				|| relativeName == null || relativeName.length() == 0)
			return templateName;
		if (templateName.charAt(0) == UrlUtils.PATH_SEPARATOR_CHAR
				|| templateName.charAt(0) == UrlUtils.WINDOWS_PATH_SEPARATOR_CHAR)
			return templateName; // 根目录开头，不添加当前路径
		return UrlUtils.getDirectoryName(relativeName) + templateName;
	}

	/**
	 * 获取不包括文件名的路径
	 *
	 * @param url 路径
	 * @return 去掉文件名的路径
	 */
	public static String getDirectoryName(String url) {
		if (url != null) {
			url = url.replace(WINDOWS_PATH_SEPARATOR_CHAR, PATH_SEPARATOR_CHAR);
			int idx = url.lastIndexOf(PATH_SEPARATOR_CHAR);
			if (idx >= 0)
				return url.substring(0, idx + 1);
		}
		return PATH_SEPARATOR;
	}

	public static final String JAR_URL_SEPARATOR = "!/";

    /** URL prefix for loading from the file system: "file:" */
    public static final String FILE_URL_PREFIX = "file:";
    
    public static List<String> listUrl(URL rootDirUrl, String[] suffixes) throws IOException {
        if ("file".equals(rootDirUrl.getProtocol())) {
            return listFile(new File(rootDirUrl.getFile()), suffixes);
        } else {
            return listJarUrl(rootDirUrl, suffixes);
        }
    }
    
    public static List<String> listFile(File file, final String[] suffixes) throws IOException {
        if (suffixes == null || suffixes.length == 0) {
            return Arrays.asList(file.list());
        } else {
            return Arrays.asList(file.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    for (String suffix : suffixes) {
                        if (name.endsWith(suffix)) {
                            return true;
                        }
                    }
                    return false;
                }
            }));
        }
    }
    
    public static List<String> listZip(ZipFile zipFile, String[] suffixes) throws IOException {
        List<String> result = new ArrayList<String>();
        for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements();) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            String entryPath = entry.getName();
            result.add(entryPath);
        }
        return result;
    }
    
    public static List<String> listJar(JarFile jarFile, String[] suffixes) throws IOException {
        List<String> result = new ArrayList<String>();
        for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
            JarEntry entry = (JarEntry) entries.nextElement();
            String entryPath = entry.getName();
            result.add(entryPath);
        }
        return result;
    }
    
    private static List<String> listJarUrl(URL rootDirUrl, String[] suffixes) throws IOException {
        URLConnection con = rootDirUrl.openConnection();
        JarFile jarFile = null;
        String jarFileUrl = null;
        String rootEntryPath = null;
        boolean newJarFile = false;
        if (con instanceof JarURLConnection) {
            // Should usually be the case for traditional JAR files.
            JarURLConnection jarCon = (JarURLConnection) con;
            jarCon.setUseCaches(false);
            jarFile = jarCon.getJarFile();
            jarFileUrl = jarCon.getJarFileURL().toExternalForm();
            JarEntry jarEntry = jarCon.getJarEntry();
            rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
        } else {
            // No JarURLConnection -> need to resort to URL file parsing.
            // We'll assume URLs of the format "jar:path!/entry", with the protocol
            // being arbitrary as long as following the entry format.
            // We'll also handle paths with and without leading "file:" prefix.
            String urlFile = rootDirUrl.getFile();
            int separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR);
            if (separatorIndex != -1) {
                jarFileUrl = urlFile.substring(0, separatorIndex);
                rootEntryPath = urlFile.substring(separatorIndex + JAR_URL_SEPARATOR.length());
                jarFile = getJarFile(jarFileUrl);
            }
            else {
                jarFile = new JarFile(urlFile);
                jarFileUrl = urlFile;
                rootEntryPath = "";
            }
            newJarFile = true;
        }
        try {
            if (!"".equals(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                // Root entry path must end with slash to allow for proper matching.
                // The Sun JRE does not return a slash here, but BEA JRockit does.
                rootEntryPath = rootEntryPath + "/";
            }
            List<String> result = new ArrayList<String>();
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = (JarEntry) entries.nextElement();
                String entryPath = entry.getName();
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    for (String suffix : suffixes) {
                        if (relativePath.endsWith(suffix)) {
                            result.add(relativePath);
                        }
                    }
                }
            }
            return result;
        } finally {
            // Close jar file, but only if freshly obtained -
            // not from JarURLConnection, which might cache the file reference.
            if (newJarFile) {
                jarFile.close();
            }
        }
    }

	private static JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(FILE_URL_PREFIX)) {
            try {
                return new JarFile(toURI(jarFileUrl).getSchemeSpecificPart());
            }
            catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new JarFile(jarFileUrl.substring(FILE_URL_PREFIX.length()));
            }
        }
        else {
            return new JarFile(jarFileUrl);
        }
    }
	
	public static URI toURI(URL url) throws URISyntaxException {
        return toURI(url.toString());
    }
	
	public static URI toURI(String location) throws URISyntaxException {
        return new URI(location.replace(" ", "%20"));
    }
	
}
